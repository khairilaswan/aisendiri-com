package com.aisendiri.shared.voice

import com.aisendiri.shared.device.DeviceProfile
import com.aisendiri.shared.model.ModelFile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** How a speech model is presented to beginners. */
enum class SpeechTier { FAST, ACCURATE }

/**
 * A Whisper speech-recognition model in whisper.cpp's format. Like the chat AIs, these are
 * weights (data), downloaded once and then used offline.
 */
data class SpeechModel(
    override val id: String,
    val tier: SpeechTier,
    val family: String,
    override val hfRepo: String,
    override val fileName: String,
    override val approxSizeMb: Int,
    val minRamGb: Double,
    val license: String,
) : ModelFile {
    // whisper.cpp files start with the uint32 0x67676d6c, stored little-endian.
    override val magic: String get() = "lmgg"
}

object SpeechCatalog {
    val all: List<SpeechModel> = listOf(
        SpeechModel(
            id = "whisper-base-q5_1",
            tier = SpeechTier.FAST,
            family = "Whisper base (multilingual)",
            hfRepo = "ggerganov/whisper.cpp",
            fileName = "ggml-base-q5_1.bin",
            approxSizeMb = 57,
            minRamGb = 2.0,
            license = "MIT",
        ),
        SpeechModel(
            id = "whisper-small-q5_1",
            tier = SpeechTier.ACCURATE,
            family = "Whisper small (multilingual)",
            hfRepo = "ggerganov/whisper.cpp",
            fileName = "ggml-small-q5_1.bin",
            approxSizeMb = 181,
            minRamGb = 3.0,
            license = "MIT",
        ),
    )

    fun byId(id: String?): SpeechModel? = all.firstOrNull { it.id == id }

    /**
     * Whisper "small" is the smallest model that understands Bahasa Melayu reasonably well,
     * so every phone that can hold it gets it. "base" is the fallback for low-memory phones.
     */
    fun recommend(device: DeviceProfile?): SpeechModel {
        val ram = device?.totalRamGb ?: return all.last()
        return all.sortedByDescending { it.approxSizeMb }.firstOrNull { ram >= it.minRamGb } ?: all.first()
    }
}

/** What language the user speaks. APP follows the app's language. */
enum class SpeechLanguage { APP, MALAY, ENGLISH, AUTO }

data class TranscribeOptions(
    /** ISO code such as "ms" or "en", or "auto" to detect. */
    val language: String,
    val threads: Int,
    /** Shrinks the audio window to the clip's length: much faster for short questions. */
    val fastMode: Boolean,
    val initialPrompt: String?,
)

/** Turns recorded speech into text on the phone. */
interface SpeechToText {
    val loadedModelId: StateFlow<String?>
    suspend fun load(model: SpeechModel, path: String): Result<Unit>
    suspend fun unload()

    /** [samples] are 16 kHz mono floats in -1..1. Cancelling the caller stops the work early. */
    suspend fun transcribe(samples: FloatArray, options: TranscribeOptions): Result<String>
}

/** Records the microphone at 16 kHz mono. Cancelling collection stops recording. */
interface VoiceRecorder {
    fun record(): Flow<FloatArray>
}

enum class SpeakerState {
    /** The voice engine is starting. */
    CHECKING,
    /** A voice for the requested language is installed and works offline. */
    READY,
    /** No voice for the language, but a close one (Indonesian for Malay) is installed. */
    CLOSE_LANGUAGE,
    /** No offline voice for the language: the user needs to install one. */
    MISSING_VOICE,
    /** The phone has no text-to-speech engine at all. */
    NO_ENGINE,
}

/** Reads text aloud with the phone's own offline voices. */
interface Speaker {
    val state: StateFlow<SpeakerState>
    /** True while anything is queued or being spoken. */
    val speaking: StateFlow<Boolean>

    /** Picks a voice for [language] ("ms" or "en"). Call again after the user installs a voice. */
    fun prepare(language: String)
    /** Adds [text] to the end of the queue. */
    fun speak(text: String, rate: Float)
    fun stop()
    /** Opens the system screen where the user can download voices. */
    fun openVoiceSettings()
    /** Name of the voice in use, for Advanced mode. */
    fun voiceDescription(): String
}

interface MicPermission {
    fun granted(): Boolean
    /** Shows the system prompt. [onResult] runs on the main thread. */
    fun request(onResult: (Boolean) -> Unit)
    /** Opens the app's system settings, for when the user said "don't ask again". */
    fun openAppSettings()
}

class VoiceServices(
    val stt: SpeechToText,
    val recorder: VoiceRecorder,
    val speaker: Speaker,
    val mic: MicPermission,
)
