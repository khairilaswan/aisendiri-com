package com.aisendiri.shared.android

import android.content.Context
import com.aisendiri.shared.platform.AppServices

fun createAndroidServices(context: Context, keepScreenOn: (Boolean) -> Unit): AppServices {
    val app = context.applicationContext
    return AppServices(
        device = AndroidDeviceInspector(app),
        models = AndroidModelRepository(app),
        engine = LlamaChatEngine(app),
        settings = PrefsSettingsStore(app),
        conversations = FileConversationStore(app),
        platform = AndroidPlatformActions(app, keepScreenOn),
    )
}
