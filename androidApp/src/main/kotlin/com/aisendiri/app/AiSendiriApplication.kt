package com.aisendiri.app

import android.app.Application
import android.net.Uri
import com.aisendiri.shared.android.createAndroidServices
import com.aisendiri.shared.ui.AppController
import kotlinx.coroutines.MainScope

class AiSendiriApplication : Application() {
    /** Lives as long as the app process, so downloads and chats survive screen rotation. */
    val controller: AppController by lazy {
        AppController(
            createAndroidServices(
                this,
                keepScreenOn = { on -> screenOnListener?.invoke(on) },
                requestMic = { onResult -> micRequester?.invoke(onResult) ?: onResult(false) },
                pickPhoto = { onResult -> photoPicker?.invoke(onResult) ?: onResult(null) },
                takePhoto = { target, onResult -> camera?.invoke(target, onResult) ?: onResult(false) },
            ),
            MainScope(),
        )
    }

    var screenOnListener: ((Boolean) -> Unit)? = null

    /** Set by the visible activity, which is the only thing that can show the permission prompt. */
    var micRequester: ((onResult: (Boolean) -> Unit) -> Unit)? = null

    /** Set by the visible activity, which opens the system photo picker and camera app. */
    var photoPicker: ((onResult: (Uri?) -> Unit) -> Unit)? = null
    var camera: ((target: Uri, onResult: (Boolean) -> Unit) -> Unit)? = null
}
