package com.aisendiri.shared.android

import android.content.Context
import com.aisendiri.llama.LlamaNative
import com.aisendiri.shared.chat.ChatMessage
import com.aisendiri.shared.chat.Role
import com.aisendiri.shared.chat.StopFilter
import com.aisendiri.shared.chat.ThoughtFilter
import com.aisendiri.shared.device.EngineConfig
import com.aisendiri.shared.model.AiModel
import com.aisendiri.shared.model.VisionAddon
import com.aisendiri.shared.platform.ChatEngine
import com.aisendiri.shared.platform.FinishReason
import com.aisendiri.shared.platform.GenerationEvent
import com.aisendiri.shared.platform.GenerationParams
import com.aisendiri.shared.platform.GenerationStats
import com.aisendiri.shared.platform.Photo
import com.aisendiri.shared.platform.PhotoStore
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import kotlin.random.Random

/** Runs llama.cpp on the phone. All native calls happen on one dedicated thread. */
class LlamaChatEngine(private val context: Context, private val photos: PhotoStore) : ChatEngine {
    private val thread = Executors.newSingleThreadExecutor { r -> Thread(r, "llama").apply { priority = Thread.MAX_PRIORITY } }
        .asCoroutineDispatcher()

    private var handle = 0L
    private var contextSize = 0
    private var threads = 0
    private val _loadedModelId = MutableStateFlow<String?>(null)
    override val loadedModelId: StateFlow<String?> = _loadedModelId
    private val _visionId = MutableStateFlow<String?>(null)
    override val visionId: StateFlow<String?> = _visionId

    private fun ensureNative() = LlamaNative.ensureInitialized(context.applicationInfo.nativeLibraryDir)

    override suspend fun load(model: AiModel, path: String, config: EngineConfig): Result<Unit> = withContext(thread) {
        runCatching {
            ensureNative()
            freeHandle()
            val h = LlamaNative.load(path, config.contextSize, config.threads, config.batchSize)
            check(h != 0L) { "Could not load ${model.fileName}" }
            handle = h
            contextSize = config.contextSize
            threads = config.threads
            _loadedModelId.value = model.id
        }
    }

    override suspend fun unload() = withContext(thread) { freeHandle() }

    override suspend fun attachVision(addon: VisionAddon, path: String, maxImageTokens: Int): Result<Unit> =
        withContext(thread) {
            runCatching {
                check(handle != 0L) { "No AI loaded" }
                if (_visionId.value == addon.id) return@runCatching
                check(LlamaNative.attachVision(handle, path, threads, maxImageTokens)) { "Could not load ${addon.fileName}" }
                _visionId.value = addon.id
            }
        }

    private fun freeHandle() {
        if (handle != 0L) LlamaNative.free(handle)
        handle = 0L
        _loadedModelId.value = null
        _visionId.value = null
    }

    override fun generate(messages: List<ChatMessage>, params: GenerationParams): Flow<GenerationEvent> = flow {
        val h = handle
        if (h == 0L) {
            emit(GenerationEvent.Failed("No AI loaded"))
            return@flow
        }
        val reserve = minOf(params.maxTokens, contextSize / 2)
        var history = messages
        var trimmed = false
        var promptTokens: Int
        var mayThink = false
        val withPhotos = _visionId.value != null
        val loaded = mutableMapOf<String, Photo>()
        while (true) {
            // Without a photo add-on the AI never sees the photo, only the user's words.
            val shown = if (withPhotos) history.filter { it.photo != null }.mapNotNull { m ->
                m.photo?.let { id -> loaded.getOrPut(id) { photos.rgb(id) ?: return@mapNotNull null } }
            } else emptyList()
            val shownIds = shown.map { it.id }.toSet()
            val marker = if (shown.isEmpty()) "" else LlamaNative.mediaMarker()
            val prompt = LlamaNative.formatChat(
                h,
                history.map { it.role.wire }.toTypedArray(),
                history.map { m ->
                    val text = if (m.photo in shownIds) "$marker\n${m.text}" else m.text
                    text.toByteArray(Charsets.UTF_8)
                }.toTypedArray(),
            )
            if (prompt == null) {
                emit(GenerationEvent.Failed("Chat template failed"))
                return@flow
            }
            mayThink = String(prompt, Charsets.UTF_8).contains("<|channel>thought")
            promptTokens = if (shown.isEmpty()) {
                LlamaNative.startCompletion(
                    h, prompt, reserve, params.temperature, params.topP, params.minP, params.repeatPenalty, Random.nextInt(),
                )
            } else {
                LlamaNative.startCompletionWithImages(
                    h, prompt,
                    shown.map { it.rgb }.toTypedArray(),
                    shown.map { it.width }.toIntArray(),
                    shown.map { it.height }.toIntArray(),
                    shown.map { it.id }.toTypedArray(),
                    reserve, params.temperature, params.topP, params.minP, params.repeatPenalty, Random.nextInt(),
                )
            }
            if (promptTokens != LlamaNative.ERR_CONTEXT_FULL) break
            // Too long: forget the oldest message after the system prompt, keeping the latest question.
            val dropAt = history.indexOfFirst { it.role != Role.SYSTEM }
            if (dropAt < 0 || history.count { it.role != Role.SYSTEM } <= 1) {
                emit(GenerationEvent.Failed("Message too long"))
                return@flow
            }
            history = history.toMutableList().also { it.removeAt(dropAt) }
            trimmed = true
        }
        if (promptTokens < 0) {
            emit(GenerationEvent.Failed("Engine error $promptTokens"))
            return@flow
        }

        val start = System.nanoTime()
        var generated = 0
        var finish = FinishReason.DONE
        val stops = StopFilter()
        val thoughts = ThoughtFilter(mayThink)
        var answerTokens = 0
        while (true) {
            currentCoroutineContext().ensureActive() // Stop button cancels here
            // Hidden notes don't count against the reply length, but are capped too.
            if (answerTokens >= params.maxTokens || generated >= params.maxTokens + MAX_NOTE_TOKENS) {
                finish = FinishReason.LENGTH
                break
            }
            val bytes = LlamaNative.nextToken(h) ?: break
            generated++
            if (thoughts.answering) answerTokens++
            val text = stops.add(thoughts.add(String(bytes, Charsets.UTF_8)))
            if (text.isNotEmpty()) emit(GenerationEvent.Text(text))
            if (stops.stopped) break
        }
        (stops.add(thoughts.flush()) + stops.flush()).takeIf { it.isNotEmpty() }?.let { emit(GenerationEvent.Text(it)) }
        if (trimmed && finish == FinishReason.DONE) finish = FinishReason.CONTEXT_FULL
        val seconds = (System.nanoTime() - start) / 1e9
        emit(GenerationEvent.Done(GenerationStats(promptTokens, generated, if (seconds > 0) generated / seconds else 0.0, finish)))
    }.flowOn(thread)

    override suspend fun diagnostics(): String = withContext(thread) {
        runCatching {
            ensureNative()
            buildString {
                if (handle != 0L) appendLine("model: ${LlamaNative.modelDescription(handle)} · ctx $contextSize")
                _visionId.value?.let { appendLine("photos: $it") }
                append(LlamaNative.systemInfo())
            }
        }.getOrElse { "engine unavailable: ${it.message}" }
    }
}

/** Most tokens of hidden notes before an answer (see [ThoughtFilter]). */
private const val MAX_NOTE_TOKENS = 1024
