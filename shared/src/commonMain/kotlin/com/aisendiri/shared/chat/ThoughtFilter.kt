package com.aisendiri.shared.chat

/**
 * Hides the notes a model writes before its answer. Gemma-SEA-LION v4.5 E2B often reasons in English
 * first, even with thinking switched off, and ends the notes with "<channel|>". The reply is held back
 * until that marker (the notes are dropped) or until the reply ends without one (all of it is shown).
 */
class ThoughtFilter(enabled: Boolean) {
    private val held = StringBuilder()

    /** False while the reply may still be notes. */
    var answering = !enabled
        private set

    fun add(chunk: String): String {
        if (answering) return chunk
        held.append(chunk)
        val end = held.indexOf(END)
        if (end < 0) return ""
        answering = true
        return held.substring(end + END.length).trimStart().also { held.clear() }
    }

    /** Held text when the reply ended without the marker: it was the answer after all. */
    fun flush(): String = held.toString().also { held.clear() }

    companion object {
        const val END = "<channel|>"
    }
}
