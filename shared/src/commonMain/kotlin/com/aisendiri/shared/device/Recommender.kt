package com.aisendiri.shared.device

import com.aisendiri.shared.model.AiModel
import com.aisendiri.shared.model.ModelCatalog

enum class Compatibility { WORKS_WELL, MAY_BE_SLOW, TOO_HEAVY, NOT_ENOUGH_STORAGE, UNSUPPORTED_PHONE }

/** Engine settings chosen for a phone. Advanced mode can override each value. */
data class EngineConfig(
    val contextSize: Int,
    val threads: Int,
    val batchSize: Int,
)

data class Recommendation(
    /** Null when no AI can run on this phone. */
    val model: AiModel?,
    val config: EngineConfig,
)

/**
 * Picks a safe AI and engine settings from the phone's real capabilities.
 * Follows the PRD rule: a smaller AI that runs reliably beats a bigger one that crashes.
 */
object Recommender {
    /** Free space to keep after a download, so the phone itself keeps working. */
    private const val STORAGE_HEADROOM_BYTES = 1024L * 1024L * 1024L

    fun compatibility(model: AiModel, device: DeviceProfile, alreadyDownloaded: Boolean = false): Compatibility {
        if (!device.is64BitArm) return Compatibility.UNSUPPORTED_PHONE
        val ram = device.totalRamGb
        return when {
            ram < model.minRamGb -> Compatibility.TOO_HEAVY
            !alreadyDownloaded && device.freeStorageBytes < model.approxSizeBytes + STORAGE_HEADROOM_BYTES ->
                Compatibility.NOT_ENOUGH_STORAGE
            ram < model.recommendedRamGb -> Compatibility.MAY_BE_SLOW
            else -> Compatibility.WORKS_WELL
        }
    }

    fun recommend(device: DeviceProfile, catalog: List<AiModel> = ModelCatalog.all): Recommendation {
        val candidates = catalog.filterNot { it.advancedOnly }.sortedByDescending { it.approxSizeMb }
        val model = candidates.firstOrNull { compatibility(it, device) == Compatibility.WORKS_WELL }
            ?: candidates.lastOrNull { compatibility(it, device) == Compatibility.MAY_BE_SLOW }
        return Recommendation(model, defaultConfig(device))
    }

    /** The next smaller AI to try when [current] fails its first-run test. */
    fun smallerThan(current: AiModel, device: DeviceProfile, catalog: List<AiModel> = ModelCatalog.all): AiModel? =
        catalog.filterNot { it.advancedOnly }
            .filter { it.approxSizeMb < current.approxSizeMb }
            .sortedByDescending { it.approxSizeMb }
            .firstOrNull { compatibility(it, device).let { c -> c == Compatibility.WORKS_WELL || c == Compatibility.MAY_BE_SLOW } }

    fun defaultConfig(device: DeviceProfile): EngineConfig {
        val ram = device.totalRamGb
        val context = when {
            ram >= 7.0 -> 4096
            else -> 2048
        }
        // Using every core usually hurts on phones: little cores slow the big ones down.
        val threads = (device.cpuCores / 2).coerceIn(2, 4)
        return EngineConfig(contextSize = context, threads = threads, batchSize = 256)
    }
}
