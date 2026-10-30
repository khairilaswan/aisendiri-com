package com.aisendiri.app

import android.Manifest
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.aisendiri.shared.ui.App

class MainActivity : ComponentActivity() {
    private var micResult: ((Boolean) -> Unit)? = null
    private val micPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        micResult?.invoke(granted)
        micResult = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val app = application as AiSendiriApplication
        val controller = app.controller

        app.screenOnListener = { on ->
            runOnUiThread {
                if (on) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }

        app.micRequester = { onResult ->
            micResult = onResult
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!controller.back()) {
                    isEnabled = false
                    app.micRequester = { onResult ->
            micResult = onResult
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }

        onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        })

        setContent { App(controller) }
    }

    override fun onDestroy() {
        (application as AiSendiriApplication).apply {
            screenOnListener = null
            micRequester = null
        }
        super.onDestroy()
    }
}
