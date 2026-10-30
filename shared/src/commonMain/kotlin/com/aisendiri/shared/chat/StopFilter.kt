package com.aisendiri.shared.chat

/**
 * Ends a streamed reply at a chat-format marker that a model writes as plain text instead of
 * its end-of-turn token. Gemma-SEA-LION, for example, sometimes ends a turn with "<|im_end|>"
 * and then keeps going with a made-up user message.
 */
class StopFilter(private val stops: List<String> = DEFAULT_STOPS) {
    private var pending = ""

    /** True once a stop marker was seen; nothing more should be generated. */
    var stopped = false
        private set

    /** Returns the text that is safe to show now. Text that could be the start of a marker is held back. */
    fun add(chunk: String): String {
        if (stopped) return ""
        pending += chunk
        val hit = stops.mapNotNull { stop -> pending.indexOf(stop).takeIf { it >= 0 } }.minOrNull()
        if (hit != null) {
            stopped = true
            return pending.substring(0, hit).also { pending = "" }
        }
        val hold = stops.maxOf { stop -> longestSuffixThatStarts(stop) }
        return pending.substring(0, pending.length - hold).also { pending = pending.substring(pending.length - hold) }
    }

    /** Text still held back when the reply ends normally. */
    fun flush(): String = pending.also { pending = "" }

    private fun longestSuffixThatStarts(stop: String): Int {
        for (n in minOf(stop.length - 1, pending.length) downTo 1) {
            if (pending.endsWith(stop.substring(0, n))) return n
        }
        return 0
    }

    companion object {
        val DEFAULT_STOPS = listOf("<|im_end|>", "<|im_start|>", "<end_of_turn>", "<start_of_turn>", "<|eot_id|>")
    }
}
