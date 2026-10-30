package com.aisendiri.shared.android

import android.content.Context
import com.aisendiri.shared.platform.AppServices
import com.aisendiri.shared.voice.VoiceServices

fun createAndroidServices(
    context: Context,
    keepScreenOn: (Boolean) -> Unit,
    requestMic: (onResult: (Boolean) -> Unit) -> Unit,
): AppServices {
    val app = context.applicationContext
    return AppServices(
        device = AndroidDeviceInspector(app),
        models = AndroidModelRepository(app),
        engine = LlamaChatEngine(app),
        settings = PrefsSettingsStore(app),
        conversations = FileConversationStore(app),
        platform = AndroidPlatformActions(app, keepScreenOn),
        voice = VoiceServices(
            stt = WhisperSpeechToText(app),
            recorder = AndroidVoiceRecorder(),
            speaker = AndroidSpeaker(app),
            mic = AndroidMicPermission(app, requestMic),
        ),
    )
}
