package com.aisendiri.shared.android

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import com.aisendiri.shared.voice.MicPermission

/** [requester] shows the system prompt; the activity provides it because only an activity can. */
class AndroidMicPermission(
    private val context: Context,
    private val requester: (onResult: (Boolean) -> Unit) -> Unit,
) : MicPermission {
    override fun granted(): Boolean =
        context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    override fun request(onResult: (Boolean) -> Unit) {
        if (granted()) onResult(true) else requester(onResult)
    }

    override fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
