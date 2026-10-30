package com.aisendiri.shared.voice

import com.aisendiri.shared.device.DeviceProfile
import com.aisendiri.shared.device.ThermalState
import kotlin.math.PI
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VoiceTest {
    private fun tone(ms: Int, amplitude: Float) = FloatArray(16 * ms) { i -> amplitude * sin(2 * PI * 220 * i / 16000).toFloat() }
    private fun noise(ms: Int, amplitude: Float) = FloatArray(16 * ms) { i -> if (i % 2 == 0) amplitude else -amplitude }

    @Test
    fun stopsAfterSpeechThenSilence() {
        val e = Endpointer()
        assertEquals(Endpointer.Result.CONTINUE, e.accept(noise(500, 0.003f)))
        assertEquals(Endpointer.Result.CONTINUE, e.accept(tone(1500, 0.2f)))
        assertTrue(e.heardSpeech)
        assertEquals(Endpointer.Result.CONTINUE, e.accept(noise(600, 0.003f)))
        assertEquals(Endpointer.Result.DONE, e.accept(noise(1000, 0.003f)))
    }

    @Test
    fun givesUpWhenNobodySpeaks() {
        val e = Endpointer(noSpeechTimeoutMs = 3000)
        assertEquals(Endpointer.Result.CONTINUE, e.accept(noise(2000, 0.004f)))
        assertEquals(Endpointer.Result.NO_SPEECH, e.accept(noise(1500, 0.004f)))
        assertFalse(e.heardSpeech)
    }

    @Test
    fun steadyBackgroundNoiseIsNotSpeech() {
        val e = Endpointer(noSpeechTimeoutMs = 4000)
        // A loud fan: well above the absolute minimum, but constant.
        assertEquals(Endpointer.Result.NO_SPEECH, e.accept(noise(4500, 0.05f)))
    }

    @Test
    fun splitsStreamingReplyIntoSentences() {
        val (first, rest) = SpeechText.takeSentences("Selamat pagi! Hari ini cuaca agak panas di Kuala Lumpur. Anda perlu ")
        assertEquals(listOf("Selamat pagi! Hari ini cuaca agak panas di Kuala Lumpur."), first)
        assertEquals(" Anda perlu ", rest)
        // A decimal point is not the end of a sentence.
        val (none, _) = SpeechText.takeSentences("Harganya RM3.50 sahaja untuk satu")
        assertTrue(none.isEmpty())
    }

    @Test
    fun cleansTextForSpeech() {
        assertEquals("Langkah penting: Minum air. Tidur awal.", SpeechText.forSpeech("**Langkah penting:** ✅\n- Minum air.\n- Tidur awal. 😊"))
        assertEquals("", SpeechText.cleanTranscript(" [BLANK_AUDIO] "))
        assertEquals("Apa khabar?", SpeechText.cleanTranscript("*muzik* Apa khabar?"))
    }

    @Test
    fun onlyNewerChipsGetTheAccurateSpeechModel() {
        fun phone(ramGb: Double, dotProd: Boolean) = DeviceProfile(
            "samsung", "test", null, "14", 8, if (dotProd) setOf("asimddp") else emptySet(), listOf("arm64-v8a"),
            (ramGb * DeviceProfile.GIB).toLong(), 0, false, 0, null, null, ThermalState.NORMAL, true, true,
        )
        // Galaxy A15 (Helio G99) has dot-product; Galaxy A51 (Exynos 9611) doesn't.
        assertEquals(SpeechTier.ACCURATE, SpeechCatalog.recommend(phone(3.6, dotProd = true)).tier)
        assertEquals(SpeechTier.FAST, SpeechCatalog.recommend(phone(3.6, dotProd = false)).tier)
        assertEquals(SpeechTier.FAST, SpeechCatalog.recommend(phone(2.7, dotProd = true)).tier)
    }
}
