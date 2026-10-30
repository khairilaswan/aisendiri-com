@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.aisendiri.shared.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
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
    val tierName = c.activeModel()?.let { s.tierName(it.tier) }

    LaunchedEffect(c.messages.size, streaming?.length) {
        val count = c.messages.size + (if (streaming != null) 1 else 0) + 1
        listState.animateScrollToItem(count - 1)
    }

    AppScaffold(
        title = s.appName,
        subtitle = if (loading) s.loadingAi else tierName?.let { "● " + s.homeStatusReady + " · " + it },
        onBack = { c.stopGeneration(); c.back() },
        actions = {
            IconButton(onClick = { c.newChat() }, enabled = !c.isGenerating) { Icon(AppIcons.Add, s.newChat) }
        },
        bottomBar = {
            Row(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)
                    .navigationBarsPadding().imePadding().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = { Text(s.chatPlaceholder) },
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                    maxLines = 6,
                    shape = RoundedCornerShape(26.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                    ),
                )
                val canSend = input.text.isNotBlank() && !loading
                val generating = c.isGenerating
                Box(
                    Modifier.size(52.dp).clip(CircleShape)
                        .background(
                            when {
                                generating -> MaterialTheme.colorScheme.secondary
                                canSend -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            },
                        )
                        .clickable(enabled = generating || canSend) {
                            if (generating) {
                                c.stopGeneration()
                            } else {
                                c.send(input.text)
                                input = TextFieldValue("")
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (generating) AppIcons.Stop else AppIcons.Send,
                        contentDescription = if (generating) s.stop else s.send,
                        tint = if (generating || canSend) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (c.messages.isEmpty() && streaming == null) {
                item {
                    Column(
                        Modifier.fillMaxWidth().padding(top = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        IconBadge(AppIcons.Sparkle, size = 72.dp, warm = true)
                        Text(s.chatEmptyTitle, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                        if (loading) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(Modifier.size(20.dp).padding(end = 0.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(10.dp))
                                Text(s.loadingAi, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        val suggestions = listOf(
                            Triple(AppIcons.Chat, s.quickWrite, s.quickWritePrompt),
                            Triple(AppIcons.Info, s.quickExplain, s.quickExplainPrompt),
                            Triple(AppIcons.Tune, s.quickSummarize, s.quickSummarizePrompt),
                            Triple(AppIcons.Translate, s.quickTranslate, s.quickTranslatePrompt),
                        )
                        suggestions.chunked(2).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                row.forEach { (icon, label, prompt) ->
                                    SuggestionCard(icon, label, Modifier.weight(1f)) {
                                        input = TextFieldValue(prompt, TextRange(prompt.length))
                                    }
                                }
                            }
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
                    MessageBubble(ChatMessage(Role.ASSISTANT, streaming.ifEmpty { s.thinking }), onCopy = null, onReport = null)
                }
            }
            item {
                Column(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    c.chatNotice?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                    }
                    if (settings.advancedMode) {
                        c.lastStats?.let {
                            Text(s.speedLabel(it.tokensPerSecond, it.generatedTokens), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Text(s.aiDisclaimer, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
private fun SuggestionCard(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 64.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconTile(icon, size = 36.dp)
            Spacer(Modifier.width(10.dp))
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun MessageBubble(msg: ChatMessage, onCopy: (() -> Unit)?, onReport: (() -> Unit)?) {
    val s = LocalStrings.current
    val mine = msg.role == Role.USER
    if (mine) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            Surface(
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(22.dp, 22.dp, 6.dp, 22.dp),
                modifier = Modifier.widthIn(max = 320.dp),
            ) {
                SelectionContainer {
                    Text(msg.text, Modifier.padding(horizontal = 16.dp, vertical = 11.dp), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        return
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        IconBadge(AppIcons.Sparkle, size = 32.dp, warm = true)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f, fill = false)) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                shape = RoundedCornerShape(6.dp, 22.dp, 22.dp, 22.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.widthIn(max = 320.dp),
            ) {
                SelectionContainer {
                    Text(msg.text, Modifier.padding(horizontal = 16.dp, vertical = 11.dp), style = MaterialTheme.typography.bodyLarge)
                }
            }
            if (onCopy != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SmallAction(AppIcons.Copy, s.copy, onCopy)
                    if (onReport != null) {
                        if (msg.reported) {
                            Text(s.reported, Modifier.padding(8.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            SmallAction(AppIcons.Flag, s.report, onReport)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SmallAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.clip(CircleShape).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
