package com.aisendiri.shared.platform

import androidx.compose.ui.graphics.ImageBitmap
import com.aisendiri.shared.chat.ChatMessage
import com.aisendiri.shared.chat.ConversationStore
import com.aisendiri.shared.device.DeviceProfile
import com.aisendiri.shared.device.EngineConfig
import com.aisendiri.shared.model.AiModel
import com.aisendiri.shared.model.ModelFile
import com.aisendiri.shared.model.VisionAddon
import com.aisendiri.shared.settings.SettingsStore
import com.aisendiri.shared.voice.VoiceServices
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface DeviceInspector {
    suspend fun inspect(): DeviceProfile
}

enum class DownloadError { NO_INTERNET, NOT_ENOUGH_STORAGE, SERVER, CORRUPT, UNKNOWN }

sealed interface DownloadState {
    data object Preparing : DownloadState
    data class Downloading(val bytes: Long, val totalBytes: Long, val bytesPerSecond: Long) : DownloadState
    data object Verifying : DownloadState
    data object Done : DownloadState
    data class Failed(val error: DownloadError, val detail: String? = null) : DownloadState
}

interface ModelRepository {
    fun isInstalled(model: ModelFile): Boolean
    fun filePath(model: ModelFile): String
    /** Bytes already saved from an interrupted download, so it can resume. */
    fun partialBytes(model: ModelFile): Long

    /**
     * Downloads, resumes and verifies [model]. Cancelling collection pauses the download;
     * collecting again resumes it.
     */
    fun download(model: ModelFile): Flow<DownloadState>
    suspend fun delete(model: ModelFile)
}

data class GenerationParams(
    val temperature: Float,
    val topP: Float,
    val minP: Float,
    val repeatPenalty: Float,
    val maxTokens: Int,
)

enum class FinishReason { DONE, LENGTH, CONTEXT_FULL }

data class GenerationStats(
    val promptTokens: Int,
    val generatedTokens: Int,
    val tokensPerSecond: Double,
    val finishReason: FinishReason,
)

sealed interface GenerationEvent {
    data class Text(val chunk: String) : GenerationEvent
    data class Done(val stats: GenerationStats) : GenerationEvent
    data class Failed(val detail: String) : GenerationEvent
}

interface ChatEngine {
    /** Id of the model currently in memory, or null. */
    val loadedModelId: StateFlow<String?>
    /** Id of the photo add-on attached to the loaded model, or null when it can't see photos. */
    val visionId: StateFlow<String?>

    suspend fun load(model: AiModel, path: String, config: EngineConfig): Result<Unit>
    suspend fun unload()

    /** Lets the loaded model see photos. [maxImageTokens] 0 keeps the add-on's own detail level. */
    suspend fun attachVision(addon: VisionAddon, path: String, maxImageTokens: Int): Result<Unit>

    /**
     * Streams a reply. Older messages are dropped automatically if the chat gets too long.
     * Photos in [messages] are only sent to the model when a photo add-on is attached.
     */
    fun generate(messages: List<ChatMessage>, params: GenerationParams): Flow<GenerationEvent>

    suspend fun diagnostics(): String
}

/** A photo as the image AI reads it: RGB bytes, 3 per pixel. */
class Photo(val id: String, val width: Int, val height: Int, val rgb: ByteArray)

/** Photos attached to chats, kept in the app's private storage. */
interface PhotoStore {
    /** Opens the system photo picker (no storage permission needed). Returns the saved photo's id. */
    suspend fun pick(): String?
    /** Opens the phone's camera app (no camera permission needed). Returns the saved photo's id. */
    suspend fun takePhoto(): String?
    suspend fun thumbnail(id: String): ImageBitmap?
    suspend fun rgb(id: String): Photo?
    suspend fun delete(ids: Collection<String>)
    suspend fun deleteAll()
}

interface PlatformActions {
    fun nowMillis(): Long
    fun systemLanguageIsMalay(): Boolean
    fun openUrl(url: String)
    fun share(text: String)
    /** Opens a pre-filled report about an AI reply (Google Play rule for AI-generated content). */
    fun reportContent(text: String, modelName: String)
    fun keepScreenOn(on: Boolean)
    val appVersion: String
}

class AppServices(
    val device: DeviceInspector,
    val models: ModelRepository,
    val engine: ChatEngine,
    val settings: SettingsStore,
    val conversations: ConversationStore,
    val platform: PlatformActions,
    val voice: VoiceServices,
    val photos: PhotoStore,
)
