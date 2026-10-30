package com.aisendiri.shared.chat

import kotlin.test.Test
import kotlin.test.assertEquals

class ThoughtFilterTest {
    private fun run(enabled: Boolean, vararg chunks: String): String {
        val f = ThoughtFilter(enabled)
        return chunks.joinToString("") { f.add(it) } + f.flush()
    }

    @Test
    fun dropsNotesBeforeMarker() {
        assertEquals("Ini durian.", run(true, "The user asks", " about a fruit.<chan", "nel|>\n", "Ini durian."))
    }

    @Test
    fun showsReplyWithoutNotesAtTheEnd() {
        val f = ThoughtFilter(true)
        assertEquals("", f.add("Ini durian."))
        assertEquals("Ini durian.", f.flush())
    }

    @Test
    fun passesThroughWhenOff() {
        assertEquals("a <channel|> b", run(false, "a <channel|>", " b"))
    }
}
