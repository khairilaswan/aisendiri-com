package com.aisendiri.shared.platform

import com.aisendiri.shared.chat.ChatMessage
import com.aisendiri.shared.chat.ConversationStore
import com.aisendiri.shared.device.DeviceProfile
import com.aisendiri.shared.device.EngineConfig
import com.aisendiri.shared.model.AiModel
import com.aisendiri.shared.settings.SettingsStore
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
    fun isInstalled(model: AiModel): Boolean
    fun filePath(model: AiModel): String
    /** Bytes already saved from an interrupted download, so it can resume. */
    fun partialBytes(model: AiModel): Long

    /**
     * Downloads, resumes and verifies [model]. Cancelling collection pauses the download;
     * collecting again resumes it.
     */
    fun download(model: AiModel): Flow<DownloadState>
    suspend fun delete(model: AiModel)
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

    suspend fun load(model: AiModel, path: String, config: EngineConfig): Result<Unit>
    suspend fun unload()

    /** Streams a reply. Older messages are dropped automatically if the chat gets too long. */
    fun generate(messages: List<ChatMessage>, params: GenerationParams): Flow<GenerationEvent>

    suspend fun diagnostics(): String
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
)
