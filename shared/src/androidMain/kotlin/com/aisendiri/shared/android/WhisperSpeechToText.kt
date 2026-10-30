package com.aisendiri.shared.android

import android.content.Context
import com.aisendiri.llama.WhisperNative
import com.aisendiri.shared.voice.SpeechModel
import com.aisendiri.shared.voice.SpeechToText
import com.aisendiri.shared.voice.TranscribeOptions
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.math.ceil

/** Runs whisper.cpp on the phone. All native calls happen on one dedicated thread. */
class WhisperSpeechToText(private val context: Context) : SpeechToText {
    private val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "whisper").apply { priority = Thread.MAX_PRIORITY } }
    private val thread = executor.asCoroutineDispatcher()

    private var handle = 0L
    private val _loadedModelId = MutableStateFlow<String?>(null)
    override val loadedModelId: StateFlow<String?> = _loadedModelId

    override suspend fun load(model: SpeechModel, path: String): Result<Unit> = withContext(thread) {
        runCatching {
            WhisperNative.ensureInitialized(context.applicationInfo.nativeLibraryDir)
            freeHandle()
            val h = WhisperNative.load(path)
            check(h != 0L) { "Could not load ${model.fileName}" }
            handle = h
            _loadedModelId.value = model.id
        }
    }

    override suspend fun unload() = withContext(thread) { freeHandle() }

    private fun freeHandle() {
        if (handle != 0L) WhisperNative.free(handle)
        handle = 0L
        _loadedModelId.value = null
    }

    override suspend fun transcribe(samples: FloatArray, options: TranscribeOptions): Result<String> {
        // whisper.cpp refuses clips under a second, so short answers like "ya" are padded with silence.
        val audio = if (samples.size >= MIN_SAMPLES) samples else samples.copyOf(MIN_SAMPLES)
        // Whisper's encoder sees 50 frames per second of audio, up to 1500 (30 s). Fast mode only
        // encodes the clip plus a margin, which is several times quicker for a short question.
        val audioCtx = if (options.fastMode) {
            (ceil(audio.size / 16_000.0 * 50).toInt() + 64).coerceAtMost(1500)
        } else {
            0
        }
        // A blocking native call can't be cancelled, so cancelling asks whisper.cpp to abort instead.
        return suspendCancellableCoroutine { cont ->
            val h = handle
            if (h == 0L) {
                cont.resume(Result.failure(IllegalStateException("No speech model loaded")))
                return@suspendCancellableCoroutine
            }
            executor.execute {
                val result = runCatching {
                    val bytes = WhisperNative.transcribe(
                        h, audio, options.language, options.threads, audioCtx,
                        options.initialPrompt?.toByteArray(Charsets.UTF_8),
                    ) ?: error("Transcription failed")
                    String(bytes, Charsets.UTF_8).trim()
                }
                if (cont.isActive) cont.resume(result)
            }
            cont.invokeOnCancellation { WhisperNative.abort(h) }
        }
    }

    private companion object {
        const val MIN_SAMPLES = 16_000 + 1_600
    }
}
