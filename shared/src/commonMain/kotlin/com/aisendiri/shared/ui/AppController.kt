package com.aisendiri.shared.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.aisendiri.shared.chat.ChatMessage
import com.aisendiri.shared.chat.Conversation
import com.aisendiri.shared.chat.Role
import com.aisendiri.shared.device.DeviceProfile
import com.aisendiri.shared.device.EngineConfig
import com.aisendiri.shared.device.Recommendation
import com.aisendiri.shared.device.Recommender
import com.aisendiri.shared.i18n.EnglishStrings
import com.aisendiri.shared.i18n.MalayStrings
import com.aisendiri.shared.i18n.Strings
import com.aisendiri.shared.model.AiModel
import com.aisendiri.shared.model.ModelCatalog
import com.aisendiri.shared.platform.AppServices
import com.aisendiri.shared.platform.DownloadState
import com.aisendiri.shared.platform.FinishReason
import com.aisendiri.shared.platform.GenerationEvent
import com.aisendiri.shared.platform.GenerationParams
import com.aisendiri.shared.platform.GenerationStats
import com.aisendiri.shared.settings.AppLanguage
import com.aisendiri.shared.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random

sealed interface Screen {
    data object Welcome : Screen
    data object CheckPhone : Screen
    data object Recommend : Screen
    data class Download(val model: AiModel) : Screen
    data class FirstRun(val model: AiModel) : Screen
    data object Home : Screen
    data class Chat(val conversationId: String?) : Screen
    data object History : Screen
    data object Settings : Screen
    data object Models : Screen
    data object Advanced : Screen
}

sealed interface EngineStatus {
    data object NotLoaded : EngineStatus
    data object Loading : EngineStatus
    data class Ready(val model: AiModel) : EngineStatus
    data class Failed(val model: AiModel) : EngineStatus
}

data class DownloadUi(val state: DownloadState?, val paused: Boolean)

sealed interface FirstRunStatus {
    data object Running : FirstRunStatus
    data class Passed(val reply: String) : FirstRunStatus
    data class Failed(val lighter: AiModel?) : FirstRunStatus
}

/** Holds app-wide state and the flows between screens. Screens only render it and call it. */
class AppController(
    val services: AppServices,
    private val scope: CoroutineScope,
) {
    val settings get() = services.settings.settings

    var stack by mutableStateOf(listOf(initialScreen()))
        private set
    val current: Screen get() = stack.last()

    var device by mutableStateOf<DeviceProfile?>(null)
        private set
    var recommendation by mutableStateOf<Recommendation?>(null)
        private set
    /** The AI shown on the recommendation screen; starts as the recommended one. */
    var chosenModel by mutableStateOf<AiModel?>(null)

    val downloads = mutableStateMapOf<String, DownloadUi>()
    private val downloadJobs = mutableMapOf<String, Job>()

    var engineStatus by mutableStateOf<EngineStatus>(EngineStatus.NotLoaded)
        private set
    private val engineMutex = Mutex()

    var firstRunStatus by mutableStateOf<FirstRunStatus>(FirstRunStatus.Running)
        private set

    var lastStats by mutableStateOf<GenerationStats?>(null)
        private set

    /** Bumped whenever an AI is downloaded or deleted, so lists refresh. */
    var modelFilesVersion by mutableStateOf(0)
        private set

    init {
        scope.launch { device = services.device.inspect() }
    }

    private fun initialScreen(): Screen =
        if (services.settings.settings.value.onboardingDone) Screen.Home else Screen.Welcome

    fun strings(s: AppSettings = settings.value): Strings = when (s.language) {
        AppLanguage.ENGLISH -> EnglishStrings
        AppLanguage.MALAY -> MalayStrings
        AppLanguage.SYSTEM -> if (services.platform.systemLanguageIsMalay()) MalayStrings else EnglishStrings
    }

    // ---- Navigation ----

    fun navigate(screen: Screen) {
        stack = stack + screen
    }

    fun replaceAll(screen: Screen) {
        stack = listOf(screen)
    }

    /** Returns false when there is nothing to go back to, so the system can close the app. */
    fun back(): Boolean {
        if (stack.size <= 1) return false
        stack = stack.dropLast(1)
        return true
    }

    // ---- Onboarding ----

    fun startPhoneCheck() {
        navigate(Screen.CheckPhone)
        scope.launch {
            val profile = services.device.inspect()
            device = profile
            val rec = Recommender.recommend(profile)
            recommendation = rec
            chosenModel = rec.model
        }
    }

    fun refreshDevice() {
        scope.launch { device = services.device.inspect() }
    }

    // ---- Downloads ----

    fun isInstalled(model: AiModel): Boolean {
        // Reading modelFilesVersion makes Compose refresh this after downloads and deletes.
        return modelFilesVersion >= 0 && services.models.isInstalled(model)
    }

    fun startDownload(model: AiModel, thenTest: Boolean) {
        if (downloadJobs[model.id]?.isActive == true) return
        services.platform.keepScreenOn(true)
        downloads[model.id] = DownloadUi(DownloadState.Preparing, paused = false)
        downloadJobs[model.id] = scope.launch {
            try {
                services.models.download(model).collect { state ->
                    downloads[model.id] = DownloadUi(state, paused = false)
                    if (state is DownloadState.Done) {
                        modelFilesVersion++
                        if (thenTest && current == Screen.Download(model)) {
                            stack = stack.dropLast(1) + Screen.FirstRun(model)
                            runFirstTest(model)
                        }
                    }
                }
            } finally {
                services.platform.keepScreenOn(false)
            }
        }
    }

    fun pauseDownload(model: AiModel) {
        downloadJobs.remove(model.id)?.cancel()
        downloads[model.id] = DownloadUi(downloads[model.id]?.state, paused = true)
    }

    fun cancelDownload(model: AiModel) {
        downloadJobs.remove(model.id)?.cancel()
        downloads.remove(model.id)
        scope.launch {
            services.models.delete(model)
            modelFilesVersion++
        }
    }

    fun deleteModel(model: AiModel) {
        scope.launch {
            if (engineStatus.let { it is EngineStatus.Ready && it.model.id == model.id }) {
                services.engine.unload()
                engineStatus = EngineStatus.NotLoaded
            }
            services.models.delete(model)
            if (settings.value.activeModelId == model.id) {
                services.settings.update { it.copy(activeModelId = null) }
            }
            downloads.remove(model.id)
            modelFilesVersion++
        }
    }

    /** Starts the download screen for a model, or skips straight to the test if it's already here. */
    fun downloadAndSetUp(model: AiModel) {
        if (services.models.isInstalled(model)) {
            navigate(Screen.FirstRun(model))
            runFirstTest(model)
        } else {
            navigate(Screen.Download(model))
            startDownload(model, thenTest = true)
        }
    }

    // ---- Engine ----

    fun engineConfig(): EngineConfig {
        val s = settings.value
        val base = device?.let { Recommender.defaultConfig(it) } ?: EngineConfig(2048, 4, 256)
        return EngineConfig(
            contextSize = s.contextSize ?: base.contextSize,
            threads = s.threads ?: base.threads,
            batchSize = base.batchSize,
        )
    }

    private fun generationParams(maxTokens: Int? = null): GenerationParams {
        val s = settings.value
        return GenerationParams(
            temperature = s.temperature,
            topP = s.topP,
            minP = s.minP,
            repeatPenalty = s.repeatPenalty,
            maxTokens = maxTokens ?: s.maxReplyTokens,
        )
    }

    fun activeModel(): AiModel? =
        ModelCatalog.byId(settings.value.activeModelId)?.takeIf { services.models.isInstalled(it) }

    /** Loads [model] (or the active AI) if it isn't already in memory. */
    suspend fun ensureEngine(model: AiModel? = activeModel()): Boolean = engineMutex.withLock {
        if (model == null) return false
        val status = engineStatus
        if (status is EngineStatus.Ready && status.model.id == model.id) return true
        engineStatus = EngineStatus.Loading
        val result = services.engine.load(model, services.models.filePath(model), engineConfig())
        engineStatus = if (result.isSuccess) EngineStatus.Ready(model) else EngineStatus.Failed(model)
        return result.isSuccess
    }

    /** Restarts the AI so new context/thread settings take effect. */
    fun reloadEngine() {
        scope.launch {
            engineMutex.withLock {
                services.engine.unload()
                engineStatus = EngineStatus.NotLoaded
            }
        }
    }

    fun useModel(model: AiModel) {
        services.settings.update { it.copy(activeModelId = model.id) }
        reloadEngine()
    }

    // ---- First-run test ----

    fun runFirstTest(model: AiModel) {
        firstRunStatus = FirstRunStatus.Running
        scope.launch {
            val dev = device ?: services.device.inspect().also { device = it }
            val loaded = ensureEngine(model)
            var reply = ""
            var failed = !loaded
            if (loaded) {
                val messages = listOf(ChatMessage(Role.USER, strings().firstRunPrompt))
                services.engine.generate(messages, generationParams(maxTokens = 48)).collect { e ->
                    when (e) {
                        is GenerationEvent.Text -> reply += e.chunk
                        is GenerationEvent.Done -> lastStats = e.stats
                        is GenerationEvent.Failed -> failed = true
                    }
                }
            }
            if (!failed && reply.isNotBlank()) {
                services.settings.update { it.copy(activeModelId = model.id, onboardingDone = true) }
                firstRunStatus = FirstRunStatus.Passed(reply.trim())
            } else {
                services.engine.unload()
                engineStatus = EngineStatus.Failed(model)
                firstRunStatus = FirstRunStatus.Failed(Recommender.smallerThan(model, dev))
            }
        }
    }

    fun tryLighter(model: AiModel) {
        chosenModel = model
        stack = listOf(Screen.Welcome, Screen.Recommend)
    }

    fun finishOnboarding() {
        replaceAll(Screen.Home)
    }

    // ---- Chat ----

    val messages = mutableStateListOf<ChatMessage>()
    var streamingText by mutableStateOf<String?>(null)
        private set
    var chatNotice by mutableStateOf<String?>(null)
        private set
    private var conversation: Conversation? = null
    private var generationJob: Job? = null
    var isGenerating by mutableStateOf(false)
        private set

    fun openChat(conversationId: String?) {
        stopGeneration()
        messages.clear()
        chatNotice = null
        conversation = null
        navigate(Screen.Chat(conversationId))
        scope.launch {
            if (conversationId != null) {
                services.conversations.get(conversationId)?.let {
                    conversation = it
                    messages.addAll(it.messages)
                }
            }
            ensureEngine()
        }
    }

    fun newChat() {
        stopGeneration()
        messages.clear()
        chatNotice = null
        conversation = null
    }

    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || isGenerating) return
        messages.add(ChatMessage(Role.USER, trimmed))
        chatNotice = null
        streamingText = ""
        isGenerating = true
        generationJob = scope.launch {
            var reply = ""
            try {
                if (!ensureEngine()) {
                    chatNotice = strings().aiError
                    return@launch
                }
                val s = strings()
                val system = settings.value.customSystemPrompt?.takeIf { it.isNotBlank() } ?: s.defaultSystemPrompt
                val history = listOf(ChatMessage(Role.SYSTEM, system)) + messages.toList()
                services.engine.generate(history, generationParams()).collect { e ->
                    when (e) {
                        is GenerationEvent.Text -> {
                            reply += e.chunk
                            streamingText = reply
                        }
                        is GenerationEvent.Done -> {
                            lastStats = e.stats
                            chatNotice = when (e.stats.finishReason) {
                                FinishReason.LENGTH -> s.replyCutShort
                                FinishReason.CONTEXT_FULL -> s.chatTooLong
                                FinishReason.DONE -> null
                            }
                        }
                        is GenerationEvent.Failed -> chatNotice = s.aiError
                    }
                }
            } finally {
                // Also runs when the user taps Stop, so a partial reply is kept.
                if (reply.isNotBlank()) messages.add(ChatMessage(Role.ASSISTANT, reply.trim()))
                streamingText = null
                isGenerating = false
                saveConversation()
            }
        }
    }

    fun stopGeneration() {
        generationJob?.cancel()
        generationJob = null
    }

    fun reportMessage(index: Int) {
        val msg = messages.getOrNull(index) ?: return
        services.platform.reportContent(msg.text, activeModel()?.family ?: "unknown")
        messages[index] = msg.copy(reported = true)
        saveConversation()
    }

    private fun saveConversation() {
        if (messages.isEmpty()) return
        val now = services.platform.nowMillis()
        val existing = conversation
        val title = messages.firstOrNull { it.role == Role.USER }?.text?.lineSequence()?.firstOrNull()?.take(48)
            ?: strings().untitledChat
        val updated = existing?.copy(updatedAt = now, messages = messages.toList())
            ?: Conversation(
                id = "$now-${Random.nextInt(0, Int.MAX_VALUE)}",
                title = title,
                createdAt = now,
                updatedAt = now,
                modelId = settings.value.activeModelId,
                messages = messages.toList(),
            )
        conversation = updated
        scope.launch { services.conversations.save(updated) }
    }

    // ---- History ----

    var conversations by mutableStateOf<List<Conversation>>(emptyList())
        private set

    fun loadHistory() {
        scope.launch { conversations = services.conversations.list().sortedByDescending { it.updatedAt } }
    }

    fun deleteConversation(id: String) {
        scope.launch {
            services.conversations.delete(id)
            loadHistory()
        }
    }

    fun deleteAllConversations() {
        scope.launch {
            services.conversations.deleteAll()
            loadHistory()
        }
    }

    // ---- Settings ----

    fun updateSettings(transform: (AppSettings) -> AppSettings) = services.settings.update(transform)
}
