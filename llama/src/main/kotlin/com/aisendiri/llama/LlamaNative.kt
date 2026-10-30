package com.aisendiri.llama

/**
 * Thin JNI binding to llama.cpp. Handles returned by [load] are not thread-safe:
 * call everything for one handle from the same thread.
 */
object LlamaNative {
    const val ERR_TOKENIZE = -1
    const val ERR_CONTEXT_FULL = -2
    const val ERR_DECODE = -3

    @Volatile
    private var initialized = false

    /** Loads the native library and the best CPU backend for this phone. Safe to call more than once. */
    @Synchronized
    fun ensureInitialized(nativeLibDir: String) {
        if (initialized) return
        System.loadLibrary("aisendiri_llama")
        init(nativeLibDir)
        initialized = true
    }

    private external fun init(nativeLibDir: String)
    external fun systemInfo(): String

    /** Returns a handle, or 0 if the model could not be loaded. */
    external fun load(path: String, nCtx: Int, nThreads: Int, nBatch: Int): Long
    external fun free(handle: Long)
    external fun modelDescription(handle: Long): String

    /** Applies the model's chat template. Text is passed as UTF-8 bytes. */
    external fun formatChat(handle: Long, roles: Array<String>, contents: Array<ByteArray>): ByteArray?

    /** Returns the number of prompt tokens, or one of the ERR_ codes. */
    external fun startCompletion(
        handle: Long,
        prompt: ByteArray,
        reserveTokens: Int,
        temperature: Float,
        topP: Float,
        minP: Float,
        repeatPenalty: Float,
        seed: Int,
    ): Int

    /** Returns UTF-8 bytes of new text (may be empty), or null when the reply is finished. */
    external fun nextToken(handle: Long): ByteArray?
}
