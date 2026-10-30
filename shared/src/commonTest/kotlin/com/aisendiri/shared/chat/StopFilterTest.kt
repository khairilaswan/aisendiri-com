package com.aisendiri.shared.chat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StopFilterTest {
    private fun run(vararg chunks: String): Pair<String, Boolean> {
        val f = StopFilter()
        val out = StringBuilder()
        for (c in chunks) {
            out.append(f.add(c))
            if (f.stopped) return out.toString() to true
        }
        return out.append(f.flush()).toString() to false
    }

    @Test
    fun plainTextPassesThrough() {
        assertEquals("Ibu negeri Sabah ialah Kota Kinabalu." to false, run("Ibu negeri ", "Sabah ialah ", "Kota Kinabalu."))
    }

    @Test
    fun stopsAtMarkerSplitAcrossChunks() {
        val (text, stopped) = run("Kota Kinabalu.", "<|im", "_end|>", "\n<|im_start|>user")
        assertEquals("Kota Kinabalu.", text)
        assertTrue(stopped)
    }

    @Test
    fun heldBackTextIsReleasedWhenItIsNotAMarker() {
        assertEquals("a < b and c <| d" to false, run("a <", " b and c <|", " d"))
    }

    @Test
    fun releasesHeldTextAtTheEnd() {
        val f = StopFilter()
        assertEquals("RM36.50 ", f.add("RM36.50 <"))
        assertFalse(f.stopped)
        assertEquals("<", f.flush())
    }

    @Test
    fun stopsAtGemma4TurnMarker() {
        assertEquals("Ini durian." to true, run("Ini durian.", "<turn|>\n<|turn>user"))
    }
}
