package com.aisendiri.shared.voice

/** Text clean-up on both sides of a voice chat: what Whisper heard, and what the phone reads aloud. */
object SpeechText {
    private val codeBlock = Regex("```[\\s\\S]*?(```|$)")
    private val bracketTag = Regex("\\[[^\\]]*]|\\*[^*]*\\*")
    private val listMarker = Regex("(?m)^\\s*([-*•]|#+)\\s+")
    private val markup = Regex("[*_#`>|~]")
    private val spaces = Regex("[ \\t]+")

    /**
     * Removes what Whisper adds for non-speech, like "[BLANK_AUDIO]" or "*muzik*".
     * Returns an empty string when nothing was really said.
     */
    fun cleanTranscript(text: String): String =
        text.replace(bracketTag, " ").replace(spaces, " ").trim()

    /** Turns a chat reply into something that sounds right read aloud: no markdown, no emoji. */
    fun forSpeech(text: String): String {
        val plain = text.replace(codeBlock, " ").replace(listMarker, "").replace(markup, "")
        val sb = StringBuilder()
        for (c in plain) {
            val skip = c.isSurrogate() || // emoji and other symbols outside the basic plane
                c == '\u200D' || c == '\uFE0F' ||
                c.code in 0x2600..0x27BF // ☀ ✅ ✨ and similar
            if (!skip) sb.append(c)
        }
        // List items often have no full stop; add one so the voice pauses between them.
        return sb.lines()
            .map { it.replace(spaces, " ").trim() }
            .filter { it.isNotEmpty() }
            .joinToString(" ") { if (it.last().isLetterOrDigit()) "$it." else it }
    }

    /**
     * Splits finished sentences off the front of a reply that is still streaming in, so the
     * phone can start speaking before the AI has finished. Short sentences are joined, so the
     * voice doesn't sound choppy. Returns the sentences and the unfinished rest.
     */
    fun takeSentences(buffer: String, minChars: Int = 40): Pair<List<String>, String> {
        val out = mutableListOf<String>()
        var start = 0
        var i = 0
        while (i < buffer.length) {
            val c = buffer[i]
            val end = when {
                c == '\n' -> true
                c in ".!?…" -> i + 1 < buffer.length && buffer[i + 1].isWhitespace()
                else -> false
            }
            if (end) {
                val chunk = buffer.substring(start, i + 1)
                if (chunk.isNotBlank() && (chunk.trim().length >= minChars || c == '\n')) {
                    out += chunk.trim()
                    start = i + 1
                }
            }
            i++
        }
        return out to buffer.substring(start)
    }
}
