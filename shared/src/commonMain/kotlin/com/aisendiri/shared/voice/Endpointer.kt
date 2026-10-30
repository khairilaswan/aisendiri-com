package com.aisendiri.shared.voice

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Decides when the user has finished speaking, from microphone loudness alone. It learns the
 * room's background noise as it goes, so it works in a quiet bedroom and on a noisy bus.
 */
class Endpointer(
    sampleRate: Int = SAMPLE_RATE,
    private val silenceToStopMs: Int = 1300,
    private val noSpeechTimeoutMs: Int = 8000,
    private val maxMs: Int = 30_000,
) {
    enum class Result { CONTINUE, DONE, NO_SPEECH }

    private val frameSize = sampleRate * FRAME_MS / 1000
    private val pending = FloatArray(frameSize)
    private var pendingCount = 0

    private var noiseFloor = -1f
    private var speechRun = 0
    private var elapsedMs = 0
    private var silenceMs = 0

    /** True once the user has said something, so there is something worth transcribing. */
    var heardSpeech = false
        private set

    /** Smoothed loudness from 0 to 1, for the on-screen meter. */
    var level = 0f
        private set

    fun accept(chunk: FloatArray): Result {
        var result = Result.CONTINUE
        for (sample in chunk) {
            pending[pendingCount++] = sample
            if (pendingCount == frameSize) {
                pendingCount = 0
                val r = frame()
                if (r != Result.CONTINUE) result = r
            }
        }
        return result
    }

    private fun frame(): Result {
        var sum = 0f
        for (s in pending) sum += s * s
        val rms = sqrt(sum / frameSize)
        level = level * 0.6f + min(1f, rms * 12f) * 0.4f

        if (noiseFloor < 0f) noiseFloor = rms
        val isSpeech = rms > max(noiseFloor * 3f, MIN_SPEECH_RMS)
        // The floor follows the room only between words: it drops at once to quiet moments
        // and rises slowly, so a steady fan or engine stops counting as speech.
        if (!isSpeech) noiseFloor = if (rms < noiseFloor) rms else noiseFloor + (rms - noiseFloor) * 0.05f

        elapsedMs += FRAME_MS
        if (isSpeech) {
            speechRun++
            silenceMs = 0
            if (speechRun >= SPEECH_FRAMES_TO_START) heardSpeech = true
        } else {
            speechRun = 0
            silenceMs += FRAME_MS
        }
        return when {
            heardSpeech && silenceMs >= silenceToStopMs -> Result.DONE
            elapsedMs >= maxMs -> if (heardSpeech) Result.DONE else Result.NO_SPEECH
            !heardSpeech && elapsedMs >= noSpeechTimeoutMs -> Result.NO_SPEECH
            else -> Result.CONTINUE
        }
    }

    companion object {
        const val SAMPLE_RATE = 16_000
        private const val FRAME_MS = 20
        private const val MIN_SPEECH_RMS = 0.012f
        private const val SPEECH_FRAMES_TO_START = 4
    }
}
