package com.aisendiri.shared.android

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.aisendiri.shared.voice.Endpointer
import com.aisendiri.shared.voice.VoiceRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/** Reads the microphone in 100 ms chunks. Audio stays in memory and is never written to disk. */
class AndroidVoiceRecorder : VoiceRecorder {
    @SuppressLint("MissingPermission") // The Talk screen asks for the microphone before recording.
    override fun record(): Flow<FloatArray> = flow {
        val rate = Endpointer.SAMPLE_RATE
        val chunk = rate / 10
        val minBuffer = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        // VOICE_RECOGNITION gives clean audio without the call-style processing that hurts recognition.
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            rate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBuffer, chunk * 2 * 4),
        )
        try {
            check(recorder.state == AudioRecord.STATE_INITIALIZED) { "Microphone unavailable" }
            recorder.startRecording()
            val shorts = ShortArray(chunk)
            while (true) {
                currentCoroutineContext().ensureActive()
                val n = recorder.read(shorts, 0, shorts.size)
                check(n >= 0) { "Microphone read failed: $n" }
                if (n > 0) emit(FloatArray(n) { shorts[it] / 32768f })
            }
        } finally {
            runCatching { recorder.stop() }
            recorder.release()
        }
    }.flowOn(Dispatchers.IO)
}
