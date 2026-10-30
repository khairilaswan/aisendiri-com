package com.aisendiri.shared.android

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.aisendiri.shared.platform.PlatformActions
import java.util.Locale

class AndroidPlatformActions(
    private val context: Context,
    private val screenOn: (Boolean) -> Unit,
) : PlatformActions {

    override fun nowMillis(): Long = System.currentTimeMillis()

    override fun systemLanguageIsMalay(): Boolean = Locale.getDefault().language == "ms"

    override fun openUrl(url: String) {
        start(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    override fun share(text: String) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        start(Intent.createChooser(send, null))
    }

    override fun reportContent(text: String, modelName: String) {
        val body = buildString {
            appendLine("I want to report this AI reply.")
            appendLine("Reason: ")
            appendLine()
            appendLine("--- AI reply ---")
            appendLine(text.take(4000))
            appendLine("---")
            appendLine("AI: $modelName")
            appendLine("App: $appVersion · ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE}")
        }
        val email = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$REPORT_EMAIL"))
            .putExtra(Intent.EXTRA_SUBJECT, "AI Sendiri: content report")
            .putExtra(Intent.EXTRA_TEXT, body)
        if (!start(email)) share(body)
    }

    override fun keepScreenOn(on: Boolean) = screenOn(on)

    override val appVersion: String =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"

    private fun start(intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        false
    }

    private companion object {
        const val REPORT_EMAIL = "report@aisendiri.com"
    }
}
