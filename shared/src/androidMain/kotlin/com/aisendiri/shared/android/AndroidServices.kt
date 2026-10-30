package com.aisendiri.shared.android

import android.content.Context
import android.net.Uri
import com.aisendiri.shared.platform.AppServices
import com.aisendiri.shared.voice.VoiceServices

fun createAndroidServices(
    context: Context,
    keepScreenOn: (Boolean) -> Unit,
    requestMic: (onResult: (Boolean) -> Unit) -> Unit,
    pickPhoto: (onResult: (Uri?) -> Unit) -> Unit,
    takePhoto: (target: Uri, onResult: (Boolean) -> Unit) -> Unit,
): AppServices {
    val app = context.applicationContext
    val photos = AndroidPhotoStore(app, pickPhoto, takePhoto)
    return AppServices(
        device = AndroidDeviceInspector(app),
        models = AndroidModelRepository(app),
        engine = LlamaChatEngine(app, photos),
        settings = PrefsSettingsStore(app),
        conversations = FileConversationStore(app),
        platform = AndroidPlatformActions(app, keepScreenOn),
        voice = VoiceServices(
            stt = WhisperSpeechToText(app),
            recorder = AndroidVoiceRecorder(),
            speaker = AndroidSpeaker(app),
            mic = AndroidMicPermission(app, requestMic),
        ),
        photos = photos,
    )
}
