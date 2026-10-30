package com.aisendiri.llama

/**
 * Thin JNI binding to whisper.cpp. Handles returned by [load] are not thread-safe:
 * call everything except [abort] for one handle from the same thread.
 */
object WhisperNative {
    @Volatile
    private var initialized = false

    /** whisper.cpp shares llama.cpp's ggml, so llama's CPU backends must be loaded first. */
    @Synchronized
    fun ensureInitialized(nativeLibDir: String) {
        if (initialized) return
        LlamaNative.ensureInitialized(nativeLibDir)
        System.loadLibrary("aisendiri_whisper")
        initialized = true
    }

    /** Returns a handle, or 0 if the model could not be loaded. */
    external fun load(path: String): Long
    external fun free(handle: Long)

    /** Asks a running [transcribe] to stop early. Safe to call from any thread. */
    external fun abort(handle: Long)

    /**
     * [samples] are 16 kHz mono floats. [audioCtx] 0 means the full 30-second window.
     * Returns UTF-8 text, or null if it failed or was aborted.
     */
    external fun transcribe(
        handle: Long,
        samples: FloatArray,
        language: String,
        threads: Int,
        audioCtx: Int,
        initialPrompt: ByteArray?,
    ): ByteArray?

    external fun systemInfo(): String
}
