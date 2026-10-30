@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.aisendiri.shared.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.aisendiri.shared.chat.ChatMessage
import com.aisendiri.shared.chat.Role
import com.aisendiri.shared.i18n.LocalStrings

@Suppress("DEPRECATION") // LocalClipboardManager: the replacement needs platform-specific clip entries.
@Composable
fun ChatScreen(c: AppController) {
    val s = LocalStrings.current
    val settings by c.settings.collectAsState()
    var input by remember { mutableStateOf(TextFieldValue("")) }
    val listState = rememberLazyListState()
    val clipboard = LocalClipboardManager.current
    val streaming = c.streamingText
    val loading = c.engineStatus is EngineStatus.Loading

    LaunchedEffect(c.messages.size, streaming?.length) {
        val count = c.messages.size + (if (streaming != null) 1 else 0) + 1
        listState.animateScrollToItem(count - 1)
    }

    AppScaffold(
        title = s.homeChatTitle,
        onBack = { c.stopGeneration(); c.back() },
        actions = {
            TextButton(onClick = { c.newChat() }, enabled = !c.isGenerating) { Text("＋ " + s.newChat) }
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(
                    Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(12.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        placeholder = { Text(s.chatPlaceholder) },
                        modifier = Modifier.weight(1f),
                        maxLines = 6,
                    )
                    if (c.isGenerating) {
                        FilledTonalButton(onClick = { c.stopGeneration() }) { Text("■ " + s.stop) }
                    } else {
                        Button(
                            onClick = {
                                c.send(input.text)
                                input = TextFieldValue("")
                            },
                            enabled = input.text.isNotBlank() && !loading,
                        ) { Text(s.send) }
                    }
                }
            }
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (c.messages.isEmpty() && streaming == null) {
                item {
                    Column(
                        Modifier.fillMaxWidth().padding(top = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("👋", style = MaterialTheme.typography.displayMedium)
                        Text(s.chatEmptyTitle, style = MaterialTheme.typography.titleLarge)
                        if (loading) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(Modifier.padding(end = 8.dp))
                                Text(s.loadingAi)
                            }
                        }
                        listOf(
                            s.quickWrite to s.quickWritePrompt,
                            s.quickExplain to s.quickExplainPrompt,
                            s.quickSummarize to s.quickSummarizePrompt,
                            s.quickTranslate to s.quickTranslatePrompt,
                        ).forEach { (label, prompt) ->
                            AssistChip(
                                onClick = { input = TextFieldValue(prompt, TextRange(prompt.length)) },
                                label = { Text(label) },
                            )
                        }
                    }
                }
            }
            itemsIndexed(c.messages) { index, msg ->
                MessageBubble(
                    msg,
                    onCopy = { clipboard.setText(AnnotatedString(msg.text)) },
                    onReport = { c.reportMessage(index) },
                )
            }
            if (streaming != null) {
                item {
                    MessageBubble(
                        ChatMessage(Role.ASSISTANT, streaming.ifEmpty { s.thinking }),
                        onCopy = null,
                        onReport = null,
                    )
                }
            }
            item {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    c.chatNotice?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    }
                    if (settings.advancedMode) {
                        c.lastStats?.let {
                            Text(s.speedLabel(it.tokensPerSecond, it.generatedTokens), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Text(s.aiDisclaimer, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(msg: ChatMessage, onCopy: (() -> Unit)?, onReport: (() -> Unit)?) {
    val s = LocalStrings.current
    val mine = msg.role == Role.USER
    Box(Modifier.fillMaxWidth(), contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart) {
        Column(horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
            Surface(
                color = if (mine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (mine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.widthIn(max = 340.dp),
            ) {
                SelectionContainer {
                    Text(msg.text, Modifier.padding(horizontal = 14.dp, vertical = 10.dp), style = MaterialTheme.typography.bodyLarge)
                }
            }
            if (!mine && onCopy != null) {
                Row {
                    TextButton(onClick = onCopy) { Text(s.copy) }
                    if (onReport != null) {
                        if (msg.reported) {
                            Text(s.reported, Modifier.padding(12.dp), style = MaterialTheme.typography.labelMedium)
                        } else {
                            TextButton(onClick = onReport) { Text("🚩 " + s.report) }
                        }
                    }
                }
            }
        }
    }
}
