package com.aisendiri.shared.device

enum class ThermalState { NORMAL, WARM, HOT, UNKNOWN }

/**
 * Hardware facts used to pick a compatible AI. Collected only from system APIs:
 * never from photos, messages, contacts or other personal data.
 */
data class DeviceProfile(
    val manufacturer: String,
    val model: String,
    val chipset: String?,
    val osVersion: String,
    val cpuCores: Int,
    val cpuFeatures: Set<String>,
    val abis: List<String>,
    val totalRamBytes: Long,
    val availableRamBytes: Long,
    val lowMemory: Boolean,
    val freeStorageBytes: Long,
    val batteryPercent: Int?,
    val isCharging: Boolean?,
    val thermal: ThermalState,
    val isOnWifi: Boolean,
    val isOnline: Boolean,
) {
    val totalRamGb: Double get() = totalRamBytes / GIB
    val is64BitArm: Boolean get() = abis.any { it == "arm64-v8a" }

    /** ARMv8.2 dot-product instructions make quantized AI noticeably faster. */
    val hasDotProd: Boolean get() = "asimddp" in cpuFeatures

    /** RAM the phone is sold as (4, 6, 8 GB...), since Android reports a bit less. */
    val marketedRamGb: Int
        get() {
            val sizes = listOf(2, 3, 4, 6, 8, 12, 16, 24)
            return sizes.firstOrNull { totalRamGb <= it * 0.97 + 0.3 } ?: totalRamGb.toInt()
        }

    companion object {
        const val GIB = 1024.0 * 1024.0 * 1024.0
    }
}
