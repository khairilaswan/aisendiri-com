package com.aisendiri.shared.android

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import com.aisendiri.shared.voice.Speaker
import com.aisendiri.shared.voice.SpeakerState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * Reads replies aloud with the phone's text-to-speech engine, using offline voices only.
 * Samsung's own engine has no Malay voice but Google's does, so Google's is preferred when installed.
 */
class AndroidSpeaker(context: Context) : Speaker {
    private val context = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val _state = MutableStateFlow(SpeakerState.CHECKING)
    override val state: StateFlow<SpeakerState> = _state
    private val _speaking = MutableStateFlow(false)
    override val speaking: StateFlow<Boolean> = _speaking

    private var tts: TextToSpeech? = null
    private var enginePackage: String? = null
    private var ready = false
    private var language = "ms"
    private var nextId = 0
    private val queued = ConcurrentHashMap.newKeySet<String>()

    private val listener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String) {}
        override fun onDone(utteranceId: String) = finished(utteranceId)
        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String) = finished(utteranceId)
        override fun onError(utteranceId: String, errorCode: Int) = finished(utteranceId)
        override fun onStop(utteranceId: String, interrupted: Boolean) = finished(utteranceId)
    }

    private fun finished(id: String) {
        if (queued.remove(id) && queued.isEmpty()) _speaking.value = false
    }

    override fun prepare(language: String) {
        this.language = language
        val engine = tts
        when {
            engine == null -> start()
            ready -> pickVoice(engine)
        }
    }

    private fun start() {
        _state.value = SpeakerState.CHECKING
        val engines = context.packageManager
            .queryIntentServices(Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE), 0)
            .map { it.serviceInfo.packageName }
        if (engines.isEmpty()) {
            _state.value = SpeakerState.NO_ENGINE
            return
        }
        enginePackage = GOOGLE_TTS.takeIf { it in engines }
        val onInit = TextToSpeech.OnInitListener { status ->
            main.post {
                val engine = tts ?: return@post
                if (status == TextToSpeech.SUCCESS) {
                    ready = true
                    engine.setOnUtteranceProgressListener(listener)
                    pickVoice(engine)
                } else {
                    engine.shutdown()
                    tts = null
                    _state.value = SpeakerState.NO_ENGINE
                }
            }
        }
        tts = enginePackage?.let { TextToSpeech(context, onInit, it) } ?: TextToSpeech(context, onInit)
    }

    private fun pickVoice(engine: TextToSpeech) {
        val voices = runCatching { engine.voices }.getOrNull().orEmpty()
            .filter { !it.isNetworkConnectionRequired && TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in it.features }
        fun best(codes: Set<String>, country: String): Voice? = voices
            .filter { it.locale.language in codes }
            .maxWithOrNull(compareBy<Voice>({ it.locale.country == country }, { it.quality }))

        val exact = if (language == "ms") best(setOf("ms"), "MY") else best(setOf(language), "US")
        // Indonesian is close enough to Malay to be understood; Java may call it "in" or "id".
        val close = if (exact == null && language == "ms") best(setOf("id", "in"), "ID") else null
        val voice = exact ?: close
        if (voice != null) engine.voice = voice
        _state.value = when {
            exact != null -> SpeakerState.READY
            close != null -> SpeakerState.CLOSE_LANGUAGE
            else -> SpeakerState.MISSING_VOICE
        }
    }

    override fun speak(text: String, rate: Float) {
        val engine = tts ?: return
        if (!ready) return
        val id = "u${nextId++}"
        queued += id
        _speaking.value = true
        engine.setSpeechRate(rate)
        if (engine.speak(text, TextToSpeech.QUEUE_ADD, null, id) == TextToSpeech.ERROR) finished(id)
    }

    override fun stop() {
        queued.clear()
        _speaking.value = false
        tts?.stop()
    }

    override fun openVoiceSettings() {
        val install = Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).apply { enginePackage?.let(::setPackage) }
        if (!start(install)) start(Intent("com.android.settings.TTS_SETTINGS"))
    }

    override fun voiceDescription(): String {
        val engine = tts ?: return "none"
        return "${enginePackage ?: engine.defaultEngine} · ${runCatching { engine.voice?.name }.getOrNull() ?: "?"} · ${_state.value}"
    }

    private fun start(intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        false
    }

    private companion object {
        const val GOOGLE_TTS = "com.google.android.tts"
    }
}
