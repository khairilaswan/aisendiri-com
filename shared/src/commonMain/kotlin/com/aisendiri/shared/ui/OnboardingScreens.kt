@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.aisendiri.shared.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.aisendiri.shared.device.Compatibility
import com.aisendiri.shared.device.Recommender
import com.aisendiri.shared.i18n.LocalStrings
import com.aisendiri.shared.i18n.MalayStrings
import com.aisendiri.shared.model.AiModel
import com.aisendiri.shared.model.ModelCatalog
import com.aisendiri.shared.platform.DownloadError
import com.aisendiri.shared.platform.DownloadState
import com.aisendiri.shared.settings.AppLanguage

@Composable
fun WelcomeScreen(c: AppController) {
    val s = LocalStrings.current
    val settings by c.settings.collectAsState()
    AppScaffold(title = null, onBack = null) { padding ->
        StepPage(
            padding = padding,
            emoji = "🤖",
            title = s.welcomeTitle,
            body = s.welcomeBody,
            primary = s.getStarted to { c.startPhoneCheck() },
        ) {
            SectionCard {
                listOf(
                    "💬" to s.welcomeFeatureChat,
                    "🎙️" to s.welcomeFeatureVoice,
                    "📷" to s.welcomeFeaturePhoto,
                    "🎨" to s.welcomeFeatureImage,
                ).forEach { (emoji, text) ->
                    Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(emoji, Modifier.padding(end = 12.dp), style = MaterialTheme.typography.titleLarge)
                        Text(text, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            Text("🔒 " + s.welcomePrivacy, style = MaterialTheme.typography.bodyMedium)
            Text(s.chooseLanguage, style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val malaySelected = c.strings(settings) === MalayStrings
                FilterChip(
                    selected = !malaySelected,
                    onClick = { c.updateSettings { it.copy(language = AppLanguage.ENGLISH) } },
                    label = { Text("English") },
                )
                FilterChip(
                    selected = malaySelected,
                    onClick = { c.updateSettings { it.copy(language = AppLanguage.MALAY) } },
                    label = { Text("Bahasa Melayu") },
                )
            }
        }
    }
}

@Composable
fun CheckPhoneScreen(c: AppController) {
    val s = LocalStrings.current
    val device = c.device
    val rec = c.recommendation
    AppScaffold(title = null, onBack = { c.back() }) { padding ->
        if (rec == null || device == null) {
            StepPage(padding, "🔍", s.checkingPhone, s.checkingPhoneBody, primary = null) {
                CircularProgressIndicator()
            }
        } else {
            val freeOk = device.freeStorageBytes > (rec.model?.approxSizeBytes ?: 0L) + 1024L * 1024 * 1024
            StepPage(
                padding, "📱", "${device.manufacturer.replaceFirstChar { it.uppercase() }} ${device.model}", null,
                primary = s.continueLabel to { c.navigate(Screen.Recommend) },
            ) {
                SectionCard {
                    InfoRow(s.phoneMemory, "${device.marketedRamGb} GB", ok = device.totalRamGb >= 2.5)
                    InfoRow(s.phoneStorage, formatBytes(device.freeStorageBytes), ok = freeOk)
                    InfoRow(s.phoneProcessor, s.coresValue(device.cpuCores), ok = device.is64BitArm)
                }
            }
        }
    }
}

@Composable
fun RecommendScreen(c: AppController) {
    val s = LocalStrings.current
    val device = c.device ?: return
    val model = c.chosenModel
    var showOptions by remember { mutableStateOf(false) }
    AppScaffold(title = null, onBack = { c.back() }) { padding ->
        when {
            showOptions -> ModelOptionsPage(c, padding, onPick = {
                c.chosenModel = it
                showOptions = false
            })
            model == null -> {
                val smallest = ModelCatalog.bySize.first()
                val storageProblem = Recommender.compatibility(smallest, device) == Compatibility.NOT_ENOUGH_STORAGE
                if (storageProblem) {
                    val need = smallest.approxSizeBytes + 1024L * 1024 * 1024 - device.freeStorageBytes
                    StepPage(
                        padding, "💾", s.notEnoughStorageTitle, s.notEnoughStorageBody(formatBytes(need)),
                        primary = s.tryAgain to { c.startPhoneCheck() },
                    )
                } else {
                    StepPage(padding, "😔", s.unsupportedTitle, s.unsupportedBody, primary = null)
                }
            }
            else -> StepPage(
                padding, "🎉", s.goodNewsTitle, s.goodNewsBody,
                primary = s.downloadYourAi to { c.downloadAndSetUp(model) },
                secondary = s.seeOtherOptions to { showOptions = true },
            ) {
                SectionCard {
                    Text(s.tierName(model.tier), style = MaterialTheme.typography.titleLarge)
                    Text(s.tierDescription(model.tier), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        compatibilityLabel(Recommender.compatibility(model, device)),
                        Modifier.padding(top = 8.dp),
                    )
                    Text(s.downloadSize(formatBytes(model.approxSizeBytes)), Modifier.padding(top = 8.dp))
                    Text(s.storageAfter(formatBytes(device.freeStorageBytes - model.approxSizeBytes)))
                }
                when {
                    !device.isOnline -> Text("📡 " + s.noInternet, color = MaterialTheme.colorScheme.error)
                    !device.isOnWifi -> Text("📶 " + s.wifiTip)
                }
            }
        }
    }
}

@Composable
private fun ModelOptionsPage(c: AppController, padding: PaddingValues, onPick: (AiModel) -> Unit) {
    val s = LocalStrings.current
    val device = c.device ?: return
    val settings by c.settings.collectAsState()
    val recommended = c.recommendation?.model
    Column(
        Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ModelCatalog.bySize.filter { settings.advancedMode || !it.advancedOnly }.forEach { m ->
            val compat = Recommender.compatibility(m, device)
            val usable = compat == Compatibility.WORKS_WELL || compat == Compatibility.MAY_BE_SLOW
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(s.tierName(m.tier), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    if (m.id == recommended?.id) Badge(s.recommendedBadge, highlight = true)
                }
                if (settings.advancedMode) Text("${m.family} · ${m.quantization}", style = MaterialTheme.typography.bodyMedium)
                Text(s.tierDescription(m.tier), style = MaterialTheme.typography.bodyMedium)
                Text(compatibilityLabel(compat), Modifier.padding(top = 6.dp))
                Text(s.downloadSize(formatBytes(m.approxSizeBytes)))
                if (usable) {
                    OutlinedButton(onClick = { onPick(m) }, Modifier.padding(top = 8.dp).fillMaxWidth()) {
                        Text(s.continueLabel)
                    }
                }
            }
        }
    }
}

@Composable
fun DownloadScreen(c: AppController, model: AiModel) {
    val s = LocalStrings.current
    val ui = c.downloads[model.id]
    AppScaffold(title = null, onBack = null) { padding ->
        StepPage(
            padding, "⬇️", s.downloadingTitle, s.downloadingBody,
            primary = when {
                ui?.paused == true -> s.resume to { c.startDownload(model, thenTest = true) }
                ui?.state is DownloadState.Failed -> s.tryAgain to { c.startDownload(model, thenTest = true) }
                ui?.state is DownloadState.Downloading -> s.pause to { c.pauseDownload(model) }
                else -> null
            },
            secondary = s.cancel to {
                c.cancelDownload(model)
                c.back()
                Unit
            },
        ) {
            SectionCard {
                Text(s.tierName(model.tier), style = MaterialTheme.typography.titleLarge)
                when (val st = ui?.state) {
                    is DownloadState.Downloading -> {
                        val fraction = if (st.totalBytes > 0) st.bytes.toFloat() / st.totalBytes else 0f
                        LinearProgressIndicator(
                            progress = { fraction },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        )
                        Text(s.downloadProgress(formatBytes(st.bytes), formatBytes(st.totalBytes)))
                        if (ui?.paused == true) {
                            Text(s.paused, fontStyle = FontStyle.Italic)
                        } else if (st.bytesPerSecond > 0) {
                            Text(s.downloadSpeed(formatBytes(st.bytesPerSecond)), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    is DownloadState.Verifying -> {
                        LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 12.dp))
                        Text(s.verifyingDownload)
                    }
                    is DownloadState.Failed -> Text(
                        when (st.error) {
                            DownloadError.NO_INTERNET -> s.downloadFailedNoInternet
                            DownloadError.NOT_ENOUGH_STORAGE -> s.downloadFailedStorage
                            DownloadError.SERVER -> s.downloadFailedServer
                            DownloadError.CORRUPT -> s.downloadFailedCorrupt
                            DownloadError.UNKNOWN -> s.downloadFailedUnknown
                        },
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    else -> {
                        LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 12.dp))
                        Text(if (ui?.paused == true) s.paused else s.preparingDownload)
                    }
                }
            }
        }
    }
}

@Composable
fun FirstRunScreen(c: AppController, model: AiModel) {
    val s = LocalStrings.current
    AppScaffold(title = null, onBack = null) { padding ->
        when (val st = c.firstRunStatus) {
            FirstRunStatus.Running -> StepPage(padding, "⚙️", s.startingAi, s.startingAiBody, primary = null) {
                CircularProgressIndicator()
            }
            is FirstRunStatus.Passed -> StepPage(
                padding, "✨", s.aiReadyTitle, s.aiReadyBody,
                primary = s.startChatting to { c.finishOnboarding() },
            ) {
                SectionCard {
                    Text(s.aiSaid, style = MaterialTheme.typography.labelLarge)
                    Text("“${st.reply}”", fontStyle = FontStyle.Italic, modifier = Modifier.padding(top = 4.dp))
                }
            }
            is FirstRunStatus.Failed -> StepPage(
                padding, "🪶", s.aiStartFailedTitle, if (st.lighter != null) s.aiStartFailedBody else s.unsupportedBody,
                primary = st.lighter?.let { lighter -> s.tryLighterAi to { c.tryLighter(lighter) } },
                secondary = s.tryAgain to { c.runFirstTest(model) },
            )
        }
    }
}
