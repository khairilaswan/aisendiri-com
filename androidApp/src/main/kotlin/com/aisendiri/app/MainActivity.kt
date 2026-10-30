package com.aisendiri.app

import android.Manifest
import android.content.ActivityNotFoundException
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.aisendiri.shared.ui.App

class MainActivity : ComponentActivity() {
    private var micResult: ((Boolean) -> Unit)? = null
    private val micPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        micResult?.invoke(granted)
        micResult = null
    }

    // The system photo picker and the camera app: neither needs a storage or camera permission.
    private var photoResult: ((Uri?) -> Unit)? = null
    private val photoPicker = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        photoResult?.invoke(uri)
        photoResult = null
    }
    private var cameraResult: ((Boolean) -> Unit)? = null
    private val camera = registerForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        cameraResult?.invoke(saved)
        cameraResult = null
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

        app.photoPicker = { onResult ->
            photoResult = onResult
            photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }

        app.camera = { target, onResult ->
            cameraResult = onResult
            try {
                camera.launch(target)
            } catch (e: ActivityNotFoundException) {
                cameraResult = null
                onResult(false) // no camera app on this phone
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!controller.back()) {
                    isEnabled = false
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
            photoPicker = null
            camera = null
        }
        super.onDestroy()
    }
}
