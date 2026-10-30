@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.aisendiri.shared.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.aisendiri.shared.device.Compatibility
import com.aisendiri.shared.device.Recommender
import com.aisendiri.shared.i18n.LocalStrings
import com.aisendiri.shared.model.AiModel
import com.aisendiri.shared.model.ModelCatalog
import com.aisendiri.shared.model.ModelFile
import com.aisendiri.shared.platform.DownloadState
import com.aisendiri.shared.settings.AppLanguage
import com.aisendiri.shared.settings.AppSettings
import com.aisendiri.shared.settings.ThemeMode
import com.aisendiri.shared.voice.SpeechCatalog
import com.aisendiri.shared.voice.SpeechLanguage
import com.aisendiri.shared.voice.SpeechModel
import kotlin.math.round

@Composable
fun HistoryScreen(c: AppController) {
    val s = LocalStrings.current
    var confirmAll by remember { mutableStateOf(false) }
    AppScaffold(
        title = s.chatHistory,
        onBack = { c.back() },
        actions = {
            if (c.conversations.isNotEmpty()) {
                IconButton(onClick = { confirmAll = true }) { Icon(AppIcons.Delete, s.deleteAllChats) }
            }
        },
    ) { padding ->
        if (c.conversations.isEmpty()) {
            Text(s.historyEmpty, Modifier.padding(padding).padding(24.dp), style = MaterialTheme.typography.bodyLarge)
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(c.conversations, key = { it.id }) { conv ->
                SectionCard {
                    ListRow(
                        AppIcons.Chat,
                        conv.title,
                        onClick = { c.openChat(conv.id) },
                        trailing = {
                            IconButton(onClick = { c.deleteConversation(conv.id) }) {
                                Icon(AppIcons.Delete, s.delete, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                    )
                }
            }
        }
    }
    if (confirmAll) {
        AlertDialog(
            onDismissRequest = { confirmAll = false },
            title = { Text(s.deleteAllChats) },
            confirmButton = {
                TextButton(onClick = { c.deleteAllConversations(); confirmAll = false }) { Text(s.delete) }
            },
            dismissButton = { TextButton(onClick = { confirmAll = false }) { Text(s.cancel) } },
        )
    }
}

@Composable
fun SettingsScreen(c: AppController) {
    val s = LocalStrings.current
    val settings by c.settings.collectAsState()
    var showPrivacy by remember { mutableStateOf(false) }
    val speaker by c.speakerState.collectAsState()
    LaunchedEffect(settings.language, settings.speechLanguage) { c.prepareVoice() }
    AppScaffold(title = s.settings, onBack = { c.back() }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionCard {
                ListRow(AppIcons.Translate, s.language)
                ChoiceRow(
                    listOf(AppLanguage.SYSTEM to s.languageSystem, AppLanguage.ENGLISH to "English", AppLanguage.MALAY to "Bahasa Melayu"),
                    settings.language,
                ) { v -> c.updateSettings { it.copy(language = v) } }
                ListRow(AppIcons.Palette, s.theme)
                ChoiceRow(
                    listOf(ThemeMode.SYSTEM to s.themeSystem, ThemeMode.LIGHT to s.themeLight, ThemeMode.DARK to s.themeDark),
                    settings.theme,
                ) { v -> c.updateSettings { it.copy(theme = v) } }
            }
            SectionCard {
                ListRow(
                    AppIcons.Mic,
                    s.speakReplies,
                    s.speakRepliesBody,
                    trailing = {
                        Switch(checked = settings.speakReplies, onCheckedChange = { v -> c.updateSettings { it.copy(speakReplies = v) } })
                    },
                )
                ListRow(AppIcons.Volume, s.voiceOutput, s.voiceStatus(speaker), onClick = { c.services.voice.speaker.openVoiceSettings() })
            }
            SectionCard {
                ListRow(AppIcons.Chip, s.myAi, s.myAiBody, onClick = { c.navigate(Screen.Models) })
                ListRow(AppIcons.Lock, s.privacy, onClick = { showPrivacy = true })
            }
            SectionCard {
                ListRow(
                    AppIcons.Tune,
                    s.advancedMode,
                    s.advancedModeBody,
                    trailing = {
                        Switch(checked = settings.advancedMode, onCheckedChange = { v -> c.updateSettings { it.copy(advancedMode = v) } })
                    },
                )
                if (settings.advancedMode) {
                    ListRow(AppIcons.Settings, s.advancedSettings, onClick = { c.navigate(Screen.Advanced) })
                }
            }
            Text(
                "${s.appName} · ${s.version(c.services.platform.appVersion)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (showPrivacy) {
        AlertDialog(
            onDismissRequest = { showPrivacy = false },
            title = { Text(s.privacy) },
            text = { Text(s.privacyBody) },
            confirmButton = { TextButton(onClick = { showPrivacy = false }) { Text("OK") } },
        )
    }
}

@Composable
fun ModelsScreen(c: AppController) {
    val s = LocalStrings.current
    val settings by c.settings.collectAsState()
    val device = c.device
    var confirmDelete by remember { mutableStateOf<ModelFile?>(null) }
    AppScaffold(title = s.myAi, onBack = { c.back() }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ModelCatalog.bySize.filter { settings.advancedMode || !it.advancedOnly }.forEach { m ->
                val installed = c.isInstalled(m)
                val inUse = settings.activeModelId == m.id && installed
                val dl = c.downloads[m.id]
                SectionCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconTile(AppIcons.Sparkle)
                        Spacer(Modifier.width(12.dp))
                        Text(s.tierName(m.tier), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                        when {
                            inUse -> Badge(s.inUse, highlight = true)
                            installed -> Badge(s.installed)
                        }
                    }
                    Text(s.tierDescription(m.tier), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 10.dp))
                    if (settings.advancedMode) {
                        Text(
                            "${m.family}\n${m.parameters} · ${m.quantization} · ${m.fileName}\n${m.hfRepo}\n${m.license}",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    if (device != null) CompatibilityLine(Recommender.compatibility(m, device, installed), Modifier.padding(top = 8.dp))
                    Text(s.downloadSize(formatBytes(m.approxSizeBytes)), style = MaterialTheme.typography.bodyMedium)
                    val st = dl?.state
                    if (!installed && st is DownloadState.Downloading) {
                        BrutalProgress(if (st.totalBytes > 0) st.bytes.toFloat() / st.totalBytes else null)
                        Text(s.downloadProgress(formatBytes(st.bytes), formatBytes(st.totalBytes)))
                    }
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        when {
                            installed && !inUse -> SecondaryButton(s.useThisAi, { c.useModel(m) }, Modifier)
                            !installed && st is DownloadState.Downloading && dl?.paused != true ->
                                SecondaryButton(s.pause, { c.pauseDownload(m) }, Modifier)
                            !installed -> {
                                val usable = device == null || Recommender.compatibility(m, device).let {
                                    it == Compatibility.WORKS_WELL || it == Compatibility.MAY_BE_SLOW
                                }
                                SecondaryButton(
                                    if (dl?.paused == true) s.resume else s.download,
                                    { c.startDownload(m, thenTest = false) },
                                    Modifier,
                                    enabled = usable || settings.advancedMode,
                                )
                            }
                        }
                        if (installed) TextButton(onClick = { confirmDelete = m }) { Text(s.delete) }
                    }
                }
            }
            Text(s.speechModelTitle, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp))
            val recommendedSpeech = SpeechCatalog.recommend(device)
            SpeechCatalog.all.filter { settings.advancedMode || c.isInstalled(it) || it == recommendedSpeech }.forEach { m ->
                SpeechModelCard(c, m, settings.advancedMode) { confirmDelete = m }
            }
        }
    }
    confirmDelete?.let { m ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            text = { Text(s.deleteAiConfirm) },
            confirmButton = { TextButton(onClick = { c.deleteModel(m); confirmDelete = null }) { Text(s.delete) } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text(s.cancel) } },
        )
    }
}

@Composable
private fun SpeechModelCard(c: AppController, m: SpeechModel, advanced: Boolean, onDelete: () -> Unit) {
    val s = LocalStrings.current
    val installed = c.isInstalled(m)
    val inUse = installed && c.installedSpeechModel() == m
    val dl = c.downloads[m.id]
    val st = dl?.state
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(AppIcons.Mic)
            Spacer(Modifier.width(12.dp))
            Text(s.speechTierName(m.tier), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            when {
                inUse -> Badge(s.inUse, highlight = true)
                installed -> Badge(s.installed)
            }
        }
        Text(s.speechTierDescription(m.tier), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 10.dp))
        if (advanced) {
            Mono("${m.family}\n${m.fileName}\n${m.hfRepo}\n${m.license}")
        }
        Text(s.downloadSize(formatBytes(m.approxSizeBytes)), style = MaterialTheme.typography.bodyMedium)
        if (!installed && st is DownloadState.Downloading) {
            BrutalProgress(if (st.totalBytes > 0) st.bytes.toFloat() / st.totalBytes else null)
            Text(s.downloadProgress(formatBytes(st.bytes), formatBytes(st.totalBytes)))
        }
        if (st is DownloadState.Failed) Text(downloadErrorText(s, st.error), color = MaterialTheme.colorScheme.error)
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            when {
                installed && !inUse -> SecondaryButton(s.useThisAi, { c.useSpeechModel(m) }, Modifier)
                !installed && st is DownloadState.Downloading && dl?.paused != true ->
                    SecondaryButton(s.pause, { c.pauseDownload(m) }, Modifier)
                !installed -> SecondaryButton(
                    if (dl?.paused == true) s.resume else s.download,
                    { c.useSpeechModel(m); c.startDownload(m, thenTest = false) },
                    Modifier,
                )
            }
            if (installed) TextButton(onClick = onDelete) { Text(s.delete) }
        }
    }
}

@Composable
fun AdvancedScreen(c: AppController) {
    val s = LocalStrings.current
    val settings by c.settings.collectAsState()
    var diagnostics by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        c.refreshDevice()
        diagnostics = c.services.engine.diagnostics()
    }
    AppScaffold(title = s.advancedSettings, onBack = { c.back() }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionCard {
                ParamSlider(s.advTemperature, settings.temperature, 0f..2f, 20) { v -> c.updateSettings { it.copy(temperature = v) } }
                Text(s.advTemperatureHelp, style = MaterialTheme.typography.bodySmall)
                ParamSlider(s.advTopP, settings.topP, 0.1f..1f, 18) { v -> c.updateSettings { it.copy(topP = v) } }
                ParamSlider(s.advMinP, settings.minP, 0f..0.5f, 10) { v -> c.updateSettings { it.copy(minP = v) } }
                ParamSlider(s.advRepeatPenalty, settings.repeatPenalty, 1f..1.5f, 10) { v -> c.updateSettings { it.copy(repeatPenalty = v) } }
                Text(s.advMaxReply, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                ChoiceRow(listOf(128, 256, 512, 1024, 2048).map { it to it.toString() }, settings.maxReplyTokens) { v ->
                    c.updateSettings { it.copy(maxReplyTokens = v) }
                }
            }
            SectionCard {
                val auto = c.engineConfig()
                Text(s.advContext, style = MaterialTheme.typography.titleSmall)
                Text(s.advContextHelp, style = MaterialTheme.typography.bodySmall)
                ChoiceRow(
                    listOf<Pair<Int?, String>>(null to s.auto) + listOf(1024, 2048, 4096, 8192).map { it to it.toString() },
                    settings.contextSize,
                ) { v -> c.updateSettings { it.copy(contextSize = v) }; c.reloadEngine() }
                Text(s.advThreads, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                ChoiceRow(
                    listOf<Pair<Int?, String>>(null to s.auto) + (1..8).map { it to it.toString() },
                    settings.threads,
                ) { v -> c.updateSettings { it.copy(threads = v) }; c.reloadEngine() }
                Text("→ ctx ${auto.contextSize} · threads ${auto.threads} · batch ${auto.batchSize}", style = MaterialTheme.typography.bodySmall)
                Text(s.advReloadNote, style = MaterialTheme.typography.bodySmall)
            }
            SectionCard {
                Text(s.advSystemPrompt, style = MaterialTheme.typography.titleSmall)
                Text(s.advSystemPromptHelp, style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = settings.customSystemPrompt ?: "",
                    onValueChange = { v -> c.updateSettings { it.copy(customSystemPrompt = v.ifBlank { null }) } },
                    placeholder = { Text(s.defaultSystemPrompt, maxLines = 3) },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    minLines = 3,
                )
            }
            SectionCard {
                Text(s.voiceSection, style = MaterialTheme.typography.titleMedium)
                Text(s.advSpeechLanguage, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                ChoiceRow(SpeechLanguage.entries.map { it to s.speechLanguageLabel(it) }, settings.speechLanguage) { v ->
                    c.updateSettings { it.copy(speechLanguage = v) }
                    c.prepareVoice()
                }
                ParamSlider(s.advSpeechRate, settings.speechRate, 0.6f..1.6f, 10) { v -> c.updateSettings { it.copy(speechRate = v) } }
                ListRow(
                    AppIcons.Tune,
                    s.advFastListening,
                    s.advFastListeningHelp,
                    trailing = {
                        Switch(checked = settings.fastListening, onCheckedChange = { v -> c.updateSettings { it.copy(fastListening = v) } })
                    },
                )
                ListRow(
                    AppIcons.Mic,
                    s.advHandsFree,
                    s.advHandsFreeHelp,
                    trailing = {
                        Switch(checked = settings.handsFree, onCheckedChange = { v -> c.updateSettings { it.copy(handsFree = v) } })
                    },
                )
                Mono("stt: ${c.services.voice.stt.loadedModelId.collectAsState().value ?: "–"}\ntts: ${c.services.voice.speaker.voiceDescription()}")
            }
            SectionCard {
                Text(s.advLastSpeed, style = MaterialTheme.typography.titleSmall)
                Text(c.lastStats?.let { s.speedLabel(it.tokensPerSecond, it.generatedTokens) + " · prompt ${it.promptTokens}" } ?: "–")
            }
            SectionCard {
                Text(s.advDevice, style = MaterialTheme.typography.titleSmall)
                c.device?.let { d ->
                    Mono(
                        """
                        ${d.manufacturer} ${d.model}
                        chipset: ${d.chipset ?: "?"}
                        os: ${d.osVersion}
                        abi: ${d.abis.joinToString()}
                        cores: ${d.cpuCores} · dotprod: ${d.hasDotProd}
                        ram: ${gb(d.totalRamBytes)} total · ${gb(d.availableRamBytes)} free · low: ${d.lowMemory}
                        storage free: ${gb(d.freeStorageBytes)}
                        battery: ${d.batteryPercent ?: "?"}% · charging: ${d.isCharging ?: "?"}
                        thermal: ${d.thermal} · wifi: ${d.isOnWifi}
                        features: ${d.cpuFeatures.sorted().joinToString(" ")}
                        """.trimIndent(),
                    )
                }
            }
            SectionCard {
                Text(s.advEngine, style = MaterialTheme.typography.titleSmall)
                Mono("status: ${c.engineStatus}\n$diagnostics")
            }
            SecondaryButton(s.advReset, { c.updateSettings(AppSettings::withAdvancedDefaults); c.reloadEngine() }, Modifier.fillMaxWidth())
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun <T> ChoiceRow(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            FilterChip(selected = value == selected, onClick = { onSelect(value) }, label = { Text(label) })
        }
    }
}

@Composable
private fun ParamSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, steps: Int, onChange: (Float) -> Unit) {
    Row(Modifier.padding(top = 8.dp)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
        Text((round(value * 100) / 100).toString(), style = MaterialTheme.typography.titleSmall)
    }
    Slider(value = value, onValueChange = { onChange(round(it * 100) / 100) }, valueRange = range, steps = steps - 1)
}

@Composable
private fun Mono(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
}

private fun gb(bytes: Long): String = formatBytes(bytes)
