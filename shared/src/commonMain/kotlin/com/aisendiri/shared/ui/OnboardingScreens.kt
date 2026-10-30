@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.aisendiri.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
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
    val malay = c.strings(settings) === MalayStrings
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Hero: navy block with the flag's stripes underneath
        Column(Modifier.fillMaxWidth().background(Malaysia.Navy)) {
            Column(
                Modifier.statusBarsPadding().padding(horizontal = 24.dp, vertical = 28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(AppIcons.Sparkle, size = 48.dp, warm = true)
                    Spacer(Modifier.width(14.dp))
                    Text(s.appName.uppercase(), style = MaterialTheme.typography.titleLarge, color = Color.White)
                }
                Text(s.welcomeTitle, style = MaterialTheme.typography.headlineMedium, color = Color.White)
                Text(s.welcomeBody, style = MaterialTheme.typography.bodyLarge, color = Malaysia.YellowTint)
            }
            StripeBand()
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FeatureRow(AppIcons.Chat, s.welcomeFeatureChat, null)
            FeatureRow(AppIcons.Mic, s.welcomeFeatureVoice, null)
            FeatureRow(AppIcons.Camera, s.welcomeFeaturePhoto, null)
            FeatureRow(AppIcons.Image, s.welcomeFeatureImage, s.comingSoon)
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(AppIcons.Lock, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 8.dp))
                Text(s.welcomePrivacy, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LanguageToggle("English", !malay, Modifier.weight(1f)) {
                    c.updateSettings { it.copy(language = AppLanguage.ENGLISH) }
                }
                LanguageToggle("Bahasa Melayu", malay, Modifier.weight(1f)) {
                    c.updateSettings { it.copy(language = AppLanguage.MALAY) }
                }
            }
            BigButton(s.getStarted, { c.startPhoneCheck() })
        }
    }
}

@Composable
private fun LanguageToggle(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    PressableBlock(onClick, if (selected) Malaysia.SkyTint else MaterialTheme.colorScheme.surface, modifier.padding(end = ShadowOffset).heightIn(min = 48.dp)) {
        Text(
            (if (selected) "✓ " else "") + label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) Malaysia.Ink else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun FeatureRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, badge: String?) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(icon, if (badge == null) Malaysia.Yellow else Malaysia.SkyTint)
            Spacer(Modifier.width(14.dp))
            Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            if (badge != null) Badge(badge)
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
            StepPage(padding, AppIcons.Phone, s.checkingPhone, s.checkingPhoneBody, primary = null) {
                CircularProgressIndicator()
            }
        } else {
            val freeOk = device.freeStorageBytes > (rec.model?.approxSizeBytes ?: 0L) + 1024L * 1024 * 1024
            StepPage(
                padding, AppIcons.Phone, "${device.manufacturer.replaceFirstChar { it.uppercase() }} ${device.model}", null,
                primary = s.continueLabel to { c.navigate(Screen.Recommend) },
            ) {
                SectionCard {
                    InfoRow(AppIcons.Chip, s.phoneMemory, "${device.marketedRamGb} GB", ok = device.totalRamGb >= 2.5)
                    InfoRow(AppIcons.Storage, s.phoneStorage, formatBytes(device.freeStorageBytes), ok = freeOk)
                    InfoRow(AppIcons.Tune, s.phoneProcessor, s.coresValue(device.cpuCores), ok = device.is64BitArm)
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
    AppScaffold(title = if (showOptions) s.seeOtherOptions else null, onBack = { if (showOptions) showOptions = false else c.back() }) { padding ->
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
                        padding, AppIcons.Storage, s.notEnoughStorageTitle, s.notEnoughStorageBody(formatBytes(need)),
                        primary = s.tryAgain to { c.startPhoneCheck() },
                    )
                } else {
                    StepPage(padding, AppIcons.Warning, s.unsupportedTitle, s.unsupportedBody, primary = null)
                }
            }
            else -> StepPage(
                padding, AppIcons.Sparkle, s.goodNewsTitle, s.goodNewsBody,
                primary = s.downloadYourAi to { c.downloadAndSetUp(model) },
                secondary = s.seeOtherOptions to { showOptions = true },
                warm = true,
            ) {
                ModelCard(model, recommended = true, device = device)
                when {
                    !device.isOnline -> Text(s.noInternet, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                    !device.isOnWifi -> Text(s.wifiTip, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
private fun ModelCard(model: AiModel, recommended: Boolean, device: com.aisendiri.shared.device.DeviceProfile, advanced: Boolean = false, action: (@Composable () -> Unit)? = null) {
    val s = LocalStrings.current
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(AppIcons.Sparkle)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(s.tierName(model.tier), style = MaterialTheme.typography.titleLarge)
                if (advanced) Text("${model.family} · ${model.quantization}", style = MaterialTheme.typography.bodySmall)
            }
            if (recommended) Badge(s.recommendedBadge, highlight = true)
        }
        Text(s.tierDescription(model.tier), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 10.dp))
        CompatibilityLine(Recommender.compatibility(model, device), Modifier.padding(top = 10.dp))
        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(AppIcons.Download, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 6.dp))
            Text(s.downloadSize(formatBytes(model.approxSizeBytes)), style = MaterialTheme.typography.bodyMedium)
        }
        if (action != null) action()
    }
}

@Composable
private fun ModelOptionsPage(c: AppController, padding: PaddingValues, onPick: (AiModel) -> Unit) {
    val s = LocalStrings.current
    val device = c.device ?: return
    val settings by c.settings.collectAsState()
    val recommended = c.recommendation?.model
    Column(
        Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ModelCatalog.bySize.filter { settings.advancedMode || !it.advancedOnly }.forEach { m ->
            val compat = Recommender.compatibility(m, device)
            val usable = compat == Compatibility.WORKS_WELL || compat == Compatibility.MAY_BE_SLOW
            ModelCard(m, m.id == recommended?.id, device, settings.advancedMode) {
                if (usable) {
                    Spacer(Modifier.height(12.dp))
                    SecondaryButton(s.continueLabel, { onPick(m) })
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
            padding, AppIcons.Download, s.downloadingTitle, s.downloadingBody,
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
                        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.Bottom) {
                            Text("${(fraction * 100).toInt()}%", style = MaterialTheme.typography.displaySmall)
                            Spacer(Modifier.weight(1f))
                            if (ui?.paused == true) {
                                Text(s.paused, fontStyle = FontStyle.Italic)
                            } else if (st.bytesPerSecond > 0) {
                                Text(s.downloadSpeed(formatBytes(st.bytesPerSecond)), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        BrutalProgress(fraction)
                        Text(s.downloadProgress(formatBytes(st.bytes), formatBytes(st.totalBytes)), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    is DownloadState.Verifying -> {
                        BrutalProgress(null)
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
                        BrutalProgress(null)
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
            FirstRunStatus.Running -> StepPage(padding, AppIcons.Sparkle, s.startingAi, s.startingAiBody, primary = null) {
                CircularProgressIndicator()
            }
            is FirstRunStatus.Passed -> StepPage(
                padding, AppIcons.CheckCircle, s.aiReadyTitle, s.aiReadyBody,
                primary = s.startChatting to { c.finishOnboarding() },
            ) {
                SectionCard {
                    Text(s.aiSaid, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text("“${st.reply}”", fontStyle = FontStyle.Italic, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 6.dp))
                }
            }
            is FirstRunStatus.Failed -> StepPage(
                padding, AppIcons.Warning, s.aiStartFailedTitle, if (st.lighter != null) s.aiStartFailedBody else s.unsupportedBody,
                primary = st.lighter?.let { lighter -> s.tryLighterAi to { c.tryLighter(lighter) } },
                secondary = s.tryAgain to { c.runFirstTest(model) },
                warm = true,
            )
        }
    }
}
