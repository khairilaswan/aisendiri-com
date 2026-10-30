@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.aisendiri.shared.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aisendiri.shared.chat.ChatMessage
import com.aisendiri.shared.chat.Role
import com.aisendiri.shared.i18n.LocalStrings
import com.aisendiri.shared.i18n.Strings
import com.aisendiri.shared.platform.DownloadError
import com.aisendiri.shared.platform.DownloadState
import com.aisendiri.shared.voice.SpeakerState

/** Voice chat: one big button to speak, the AI answers aloud. The conversation is saved like a normal chat. */
@Suppress("DEPRECATION") // LocalClipboardManager: the replacement needs platform-specific clip entries.
@Composable
fun TalkScreen(c: AppController) {
    val s = LocalStrings.current
    val settings by c.settings.collectAsState()
    val speaker by c.speakerState.collectAsState()
    val speechModel = c.installedSpeechModel()
    val loading = c.engineStatus is EngineStatus.Loading
    val tierName = c.activeModel()?.let { s.tierName(it.tier) }

    // Re-pick the phone voice when the language changes.
    LaunchedEffect(settings.language, settings.speechLanguage) { c.prepareVoice() }

    AppScaffold(
        title = s.homeTalkTitle,
        subtitle = if (loading) s.loadingAi else tierName?.let { "● " + s.homeStatusReady + " · " + it },
        onBack = { c.back() },
        actions = {
            SquareIconButton(
                AppIcons.Add, s.newChat, { c.stopTalking(); c.newChat() },
                Modifier.padding(end = 12.dp), color = Malaysia.Yellow,
                enabled = c.talkPhase == TalkPhase.IDLE,
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).navigationBarsPadding()) {
            if (speechModel == null) {
                VoiceSetup(c, Modifier.weight(1f))
            } else {
                Conversation(c, Modifier.weight(1f))
                VoiceNotices(c, settings.speakReplies, speaker)
                MicArea(c)
            }
        }
    }
}

@Composable
private fun VoiceNotices(c: AppController, speakReplies: Boolean, speaker: SpeakerState) {
    val s = LocalStrings.current
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        c.fasterSpeechSuggestion()?.let { fast ->
            val dl = c.downloads[fast.id]?.state
            SectionCard(color = Malaysia.SkyTint) {
                Text(s.fasterVoiceTip, style = MaterialTheme.typography.bodyMedium, color = Malaysia.Ink)
                if (dl is DownloadState.Downloading) {
                    BrutalProgress(if (dl.totalBytes > 0) dl.bytes.toFloat() / dl.totalBytes else null)
                } else {
                    Spacer(Modifier.height(10.dp))
                    SecondaryButton(s.useFasterVoice(formatBytes(fast.approxSizeBytes)), { c.switchToFasterSpeech(fast) })
                }
            }
        }
        if (speakReplies) SpeakerBanner(c, speaker)
        if (c.micDenied) {
            SectionCard(color = Malaysia.RedTint) {
                Text(s.micDenied, style = MaterialTheme.typography.bodyMedium, color = Malaysia.Ink)
                Spacer(Modifier.height(10.dp))
                SecondaryButton(s.openAppSettings, { c.openAppSettings() })
            }
        }
    }
}

@Suppress("DEPRECATION")
@Composable
private fun Conversation(c: AppController, modifier: Modifier) {
    val s = LocalStrings.current
    val clipboard = LocalClipboardManager.current
    val listState = rememberLazyListState()
    val streaming = c.streamingText
    LaunchedEffect(c.messages.size, streaming?.length) {
        val count = c.messages.size + (if (streaming != null) 1 else 0) + 1
        listState.animateScrollToItem(count - 1)
    }
    LazyColumn(
        modifier.fillMaxWidth(),
        state = listState,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (c.messages.isEmpty() && streaming == null) {
            item {
                Column(
                    Modifier.fillMaxWidth().padding(top = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    IconBadge(AppIcons.Mic, size = 72.dp)
                    Text(s.talkEmptyTitle, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
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
            item { MessageBubble(ChatMessage(Role.ASSISTANT, streaming.ifEmpty { s.thinking }), onCopy = null, onReport = null) }
        }
        item {
            Text(
                s.aiDisclaimer,
                Modifier.fillMaxWidth().padding(top = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** The big round button, its loudness ring, and a line saying what's happening. */
@Composable
private fun MicArea(c: AppController) {
    val s = LocalStrings.current
    val phase = c.talkPhase
    val level by animateFloatAsState(if (phase == TalkPhase.LISTENING) c.micLevel else 0f)
    val (color, iconTint) = when (phase) {
        TalkPhase.IDLE -> Malaysia.Yellow to Malaysia.Ink
        TalkPhase.LISTENING -> Malaysia.Red to Color.White
        TalkPhase.TRANSCRIBING, TalkPhase.THINKING -> Malaysia.SkyTint to Malaysia.Ink
        TalkPhase.SPEAKING -> Malaysia.Navy to Color.White
    }
    val status = when (phase) {
        TalkPhase.IDLE -> s.talkTapToSpeak
        TalkPhase.LISTENING -> s.talkListening
        TalkPhase.TRANSCRIBING -> s.talkTranscribing
        TalkPhase.THINKING -> s.thinking
        TalkPhase.SPEAKING -> s.talkSpeaking
    }
    Column(
        Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 20.dp, start = 16.dp, end = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        (c.talkNotice ?: c.chatNotice)?.let {
            Text(
                it,
                Modifier.padding(bottom = 12.dp),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }
        Box(Modifier.size(176.dp), contentAlignment = Alignment.Center) {
            // The ring grows with the user's voice, so they can see they're being heard.
            if (phase == TalkPhase.LISTENING) {
                Box(Modifier.size(128.dp + 48.dp * level).brutal(CircleShape, Malaysia.RedTint, ink, shadow = 0.dp))
            }
            PressableBlock(
                onClick = { c.talkButton() },
                color = color,
                modifier = Modifier.padding(end = ShadowOffset, bottom = ShadowOffset).size(124.dp),
                shape = CircleShape,
            ) {
                when (phase) {
                    TalkPhase.TRANSCRIBING, TalkPhase.THINKING -> Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(64.dp), color = Malaysia.Navy, strokeWidth = 5.dp)
                        Icon(AppIcons.Stop, s.stop, tint = Malaysia.Ink, modifier = Modifier.size(28.dp))
                    }
                    TalkPhase.SPEAKING -> Icon(AppIcons.Stop, s.stop, tint = iconTint, modifier = Modifier.size(52.dp))
                    else -> Icon(AppIcons.Mic, s.talkStartSpeaking, tint = iconTint, modifier = Modifier.size(60.dp))
                }
            }
        }
        Text(status, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        if (phase == TalkPhase.LISTENING) {
            Text(
                s.talkListeningHint,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Explains when replies can't be read aloud in the right language, with a way to fix it. */
@Composable
private fun SpeakerBanner(c: AppController, state: SpeakerState) {
    val s = LocalStrings.current
    val text = when (state) {
        SpeakerState.CLOSE_LANGUAGE -> s.voiceCloseLanguage
        SpeakerState.MISSING_VOICE -> s.voiceMissing
        SpeakerState.NO_ENGINE -> s.voiceNoEngine
        SpeakerState.CHECKING, SpeakerState.READY -> return
    }
    SectionCard(color = Malaysia.YellowTint) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = Malaysia.Ink)
        if (state != SpeakerState.NO_ENGINE) {
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton(s.installVoice, { c.services.voice.speaker.openVoiceSettings() }, Modifier.weight(1f))
                SecondaryButton(s.checkAgain, { c.prepareVoice() }, Modifier.weight(1f))
            }
        }
    }
}

/** First visit: download the speech file and allow the microphone. */
@Composable
private fun VoiceSetup(c: AppController, modifier: Modifier) {
    val s = LocalStrings.current
    val model = c.speechModel()
    val ui = c.downloads[model.id]
    val st = ui?.state
    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically),
    ) {
        IconBadge(AppIcons.Mic)
        Text(s.voiceSetupTitle, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Text(
            s.voiceSetupBody,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!c.services.voice.mic.granted()) {
            Text(s.micPermissionBody, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        }
        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(AppIcons.Mic)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(s.speechTierName(model.tier), style = MaterialTheme.typography.titleMedium)
                    Text(s.downloadSize(formatBytes(model.approxSizeBytes)), style = MaterialTheme.typography.bodyMedium)
                }
            }
            when {
                st is DownloadState.Downloading -> {
                    BrutalProgress(if (st.totalBytes > 0) st.bytes.toFloat() / st.totalBytes else null)
                    Text(
                        if (ui?.paused == true) s.paused else s.downloadProgress(formatBytes(st.bytes), formatBytes(st.totalBytes)),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                st is DownloadState.Verifying || st is DownloadState.Preparing -> {
                    BrutalProgress(null)
                    Text(if (st is DownloadState.Verifying) s.verifyingDownload else s.preparingDownload)
                }
                st is DownloadState.Failed -> Text(
                    downloadErrorText(s, st.error),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
        val downloading = st is DownloadState.Downloading && ui?.paused != true
        if (downloading) {
            SecondaryButton(s.pause, { c.pauseDownload(model) })
        } else if (st !is DownloadState.Verifying && st !is DownloadState.Preparing) {
            BigButton(
                when {
                    ui?.paused == true -> s.resume
                    st is DownloadState.Failed -> s.tryAgain
                    else -> s.voiceDownload(formatBytes(model.approxSizeBytes))
                },
                {
                    if (!c.services.voice.mic.granted()) c.requestMic()
                    c.startDownload(model, thenTest = false)
                },
            )
        }
        Text(s.wifiTip, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

internal fun downloadErrorText(s: Strings, error: DownloadError): String = when (error) {
    DownloadError.NO_INTERNET -> s.downloadFailedNoInternet
    DownloadError.NOT_ENOUGH_STORAGE -> s.downloadFailedStorage
    DownloadError.SERVER -> s.downloadFailedServer
    DownloadError.CORRUPT -> s.downloadFailedCorrupt
    DownloadError.UNKNOWN -> s.downloadFailedUnknown
}
