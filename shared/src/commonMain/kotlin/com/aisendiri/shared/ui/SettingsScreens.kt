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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.aisendiri.shared.platform.DownloadState
import com.aisendiri.shared.settings.AppLanguage
import com.aisendiri.shared.settings.AppSettings
import com.aisendiri.shared.settings.ThemeMode
import kotlin.math.round

@Composable
fun HistoryScreen(c: AppController) {
    val s = LocalStrings.current
    var confirmAll by remember { mutableStateOf(false) }
    AppScaffold(
        title = s.chatHistory,
        onBack = { c.back() },
        actions = {
            if (c.conversations.isNotEmpty()) TextButton(onClick = { confirmAll = true }) { Text(s.delete) }
        },
    ) { padding ->
        if (c.conversations.isEmpty()) {
            Text(s.historyEmpty, Modifier.padding(padding).padding(24.dp), style = MaterialTheme.typography.bodyLarge)
        }
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            items(c.conversations, key = { it.id }) { conv ->
                Row(
                    Modifier.fillMaxWidth().clickable { c.openChat(conv.id) }.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("💬 " + conv.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, maxLines = 2)
                    TextButton(onClick = { c.deleteConversation(conv.id) }) { Text("🗑") }
                }
                HorizontalDivider()
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
    AppScaffold(title = s.settings, onBack = { c.back() }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionCard {
                Text(s.language, style = MaterialTheme.typography.titleMedium)
                ChoiceRow(
                    listOf(AppLanguage.SYSTEM to s.languageSystem, AppLanguage.ENGLISH to "English", AppLanguage.MALAY to "Bahasa Melayu"),
                    settings.language,
                ) { v -> c.updateSettings { it.copy(language = v) } }
                Text(s.theme, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                ChoiceRow(
                    listOf(ThemeMode.SYSTEM to s.themeSystem, ThemeMode.LIGHT to s.themeLight, ThemeMode.DARK to s.themeDark),
                    settings.theme,
                ) { v -> c.updateSettings { it.copy(theme = v) } }
            }
            ClickableCard("🧠 " + s.myAi, s.myAiBody) { c.navigate(Screen.Models) }
            ClickableCard("🔒 " + s.privacy, null) { showPrivacy = true }
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("🛠️ " + s.advancedMode, style = MaterialTheme.typography.titleMedium)
                        Text(s.advancedModeBody, style = MaterialTheme.typography.bodyMedium)
                    }
                    Switch(checked = settings.advancedMode, onCheckedChange = { v -> c.updateSettings { it.copy(advancedMode = v) } })
                }
                if (settings.advancedMode) {
                    OutlinedButton(onClick = { c.navigate(Screen.Advanced) }, Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        Text(s.advancedSettings)
                    }
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
    var confirmDelete by remember { mutableStateOf<AiModel?>(null) }
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
                        Text(s.tierName(m.tier), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                        when {
                            inUse -> Badge(s.inUse, highlight = true)
                            installed -> Badge(s.installed)
                        }
                    }
                    Text(s.tierDescription(m.tier), style = MaterialTheme.typography.bodyMedium)
                    if (settings.advancedMode) {
                        Text(
                            "${m.family}\n${m.parameters} · ${m.quantization} · ${m.fileName}\n${m.hfRepo}\n${m.license}",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    if (device != null) Text(compatibilityLabel(Recommender.compatibility(m, device, installed)), Modifier.padding(top = 6.dp))
                    Text(s.downloadSize(formatBytes(m.approxSizeBytes)), style = MaterialTheme.typography.bodyMedium)
                    val st = dl?.state
                    if (!installed && st is DownloadState.Downloading) {
                        LinearProgressIndicator(
                            progress = { if (st.totalBytes > 0) st.bytes.toFloat() / st.totalBytes else 0f },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        )
                        Text(s.downloadProgress(formatBytes(st.bytes), formatBytes(st.totalBytes)))
                    }
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        when {
                            installed && !inUse -> OutlinedButton(onClick = { c.useModel(m) }) { Text(s.useThisAi) }
                            !installed && st is DownloadState.Downloading && dl?.paused != true ->
                                OutlinedButton(onClick = { c.pauseDownload(m) }) { Text(s.pause) }
                            !installed -> {
                                val usable = device == null || Recommender.compatibility(m, device).let {
                                    it == Compatibility.WORKS_WELL || it == Compatibility.MAY_BE_SLOW
                                }
                                OutlinedButton(
                                    onClick = { c.startDownload(m, thenTest = false) },
                                    enabled = usable || settings.advancedMode,
                                ) { Text(if (dl?.paused == true) s.resume else s.download) }
                            }
                        }
                        if (installed) TextButton(onClick = { confirmDelete = m }) { Text(s.delete) }
                    }
                }
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
            OutlinedButton(onClick = { c.updateSettings(AppSettings::withAdvancedDefaults); c.reloadEngine() }, Modifier.fillMaxWidth()) {
                Text(s.advReset)
            }
        }
    }
}

@Composable
private fun ClickableCard(title: String, body: String?, onClick: () -> Unit) {
    SectionCard(Modifier.clickable(onClick = onClick)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (body != null) Text(body, style = MaterialTheme.typography.bodyMedium)
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
