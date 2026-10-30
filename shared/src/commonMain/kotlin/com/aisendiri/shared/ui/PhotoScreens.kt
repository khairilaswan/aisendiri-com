@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.aisendiri.shared.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aisendiri.shared.i18n.LocalStrings
import com.aisendiri.shared.platform.DownloadState

/**
 * Shown before the first photo: downloads the photo add-on for the AI in use, or explains
 * which AI can see photos when the one in use can't.
 */
@Composable
fun PhotoSetupScreen(c: AppController) {
    val s = LocalStrings.current
    val addon = c.visionAddon()
    AppScaffold(title = s.homePhotoTitle, onBack = { c.back() }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).navigationBarsPadding().verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically),
        ) {
            IconBadge(AppIcons.Camera)
            if (addon == null) {
                Text(s.photoUnsupportedTitle, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                Text(
                    s.photoUnsupportedBody,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                BigButton(s.photoUseSmart, { c.back(); c.navigate(Screen.Models) })
                return@Column
            }
            Text(s.photoSetupTitle, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            Text(
                s.photoSetupBody,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val files = addon.files
            val states = files.map { c.downloads[it.id] }
            val total = addon.totalSizeBytes
            val done = files.zip(states).sumOf { (f, ui) ->
                if (c.isInstalled(f)) f.approxSizeBytes else (ui?.state as? DownloadState.Downloading)?.bytes ?: 0L
            }
            val downloading = states.any { it != null && it.state is DownloadState.Downloading && !it.paused }
            val paused = states.any { it?.paused == true }
            val busy = states.any { it?.state is DownloadState.Verifying || it?.state is DownloadState.Preparing }
            val failed = states.firstNotNullOfOrNull { it?.state as? DownloadState.Failed }
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconTile(AppIcons.Camera)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(s.photoAddonName, style = MaterialTheme.typography.titleMedium)
                        Text(s.downloadSize(formatBytes(total)), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                when {
                    downloading || paused -> {
                        BrutalProgress(if (total > 0) done.toFloat() / total else null)
                        Text(
                            if (paused && !downloading) s.paused else s.downloadProgress(formatBytes(done), formatBytes(total)),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    busy -> {
                        BrutalProgress(null)
                        Text(s.verifyingDownload)
                    }
                    failed != null -> Text(
                        downloadErrorText(s, failed.error),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
            if (downloading) {
                SecondaryButton(s.pause, {
                    files.filter { c.downloads[it.id]?.state is DownloadState.Downloading }.forEach { c.pauseDownload(it) }
                })
            } else if (!busy) {
                BigButton(
                    when {
                        paused -> s.resume
                        failed != null -> s.tryAgain
                        else -> s.photoDownload(formatBytes(total))
                    },
                    { c.downloadPhotoAddon() },
                )
            }
            Text(s.wifiTip, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}
