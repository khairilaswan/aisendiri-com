package com.aisendiri.app

import android.app.Application
import com.aisendiri.shared.android.createAndroidServices
import com.aisendiri.shared.ui.AppController
import kotlinx.coroutines.MainScope

class AiSendiriApplication : Application() {
    /** Lives as long as the app process, so downloads and chats survive screen rotation. */
    val controller: AppController by lazy {
        AppController(createAndroidServices(this) { on -> screenOnListener?.invoke(on) }, MainScope())
    }

    var screenOnListener: ((Boolean) -> Unit)? = null
}
