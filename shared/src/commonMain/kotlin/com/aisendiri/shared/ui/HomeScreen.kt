@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.aisendiri.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.aisendiri.shared.i18n.LocalStrings

@Composable
fun HomeScreen(c: AppController) {
    val s = LocalStrings.current
    val settings by c.settings.collectAsState()
    // Re-evaluated when the active AI or downloaded files change.
    val active = remember(settings.activeModelId, c.modelFilesVersion) { c.activeModel() }
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState()),
    ) {
        // Hero header: navy block with the flag's stripes underneath
        Column(Modifier.fillMaxWidth().background(Malaysia.Navy)) {
            Column(
                Modifier.statusBarsPadding().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(AppIcons.Sparkle, size = 42.dp, warm = true)
                    Spacer(Modifier.width(12.dp))
                    Text(s.appName.uppercase(), style = MaterialTheme.typography.titleLarge, color = Color.White, modifier = Modifier.weight(1f))
                    SquareIconButton(AppIcons.Settings, s.settings, { c.navigate(Screen.Settings) }, color = Color.White)
                }
                Text(s.homeGreeting, style = MaterialTheme.typography.headlineMedium, color = Color.White)
                if (active != null) {
                    Row(
                        Modifier.brutal(CircleShape, Malaysia.Yellow, Malaysia.Ink, shadow = 0.dp).padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(10.dp).brutal(CircleShape, Malaysia.Green, Malaysia.Ink, shadow = 0.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(s.homeStatusReady + " · " + s.tierName(active.tier), style = MaterialTheme.typography.labelLarge, color = Malaysia.Ink)
                    }
                }
            }
            StripeBand()
        }

        Column(
            Modifier.padding(16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (active == null) {
                SectionCard(color = Malaysia.YellowTint) {
                    Text(s.homeStatusNoAi, style = MaterialTheme.typography.titleMedium, color = Malaysia.Ink)
                    Spacer(Modifier.size(12.dp))
                    BigButton(s.setUpAi, { c.startPhoneCheck() })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FeatureTile(AppIcons.Chat, s.homeChatTitle, s.homeChatBody, null, Malaysia.Yellow, enabled = active != null, Modifier.weight(1f)) {
                    c.openChat(null)
                }
                FeatureTile(AppIcons.Mic, s.homeTalkTitle, s.homeTalkBody, s.comingSoon, Malaysia.SkyTint, enabled = false, Modifier.weight(1f)) {}
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FeatureTile(AppIcons.Camera, s.homePhotoTitle, s.homePhotoBody, s.comingSoon, Malaysia.RedTint, enabled = false, Modifier.weight(1f)) {}
                FeatureTile(AppIcons.Image, s.homeImageTitle, s.homeImageBody, s.comingSoon, Malaysia.YellowTint, enabled = false, Modifier.weight(1f)) {}
            }
            SectionCard {
                ListRow(AppIcons.History, s.chatHistory, onClick = { c.loadHistory(); c.navigate(Screen.History) })
                ListRow(AppIcons.Chip, s.myAi, s.myAiBody, onClick = { c.navigate(Screen.Models) })
                ListRow(AppIcons.Settings, s.settings, onClick = { c.navigate(Screen.Settings) })
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Icon(AppIcons.Lock, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(s.welcomePrivacy, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun FeatureTile(
    icon: ImageVector,
    title: String,
    body: String,
    badge: String?,
    color: Color,
    enabled: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    // Coming-soon tiles keep their colour so the grid stays bright, but look muted and don't react.
    PressableBlock(
        onClick,
        color,
        modifier.padding(end = ShadowOffset, bottom = ShadowOffset).aspectRatio(0.9f),
        enabled = enabled,
        shape = MaterialTheme.shapes.large,
        disabledColor = color,
    ) {
        Box(Modifier.fillMaxSize().padding(14.dp)) {
            Column(Modifier.fillMaxSize().alpha(if (badge == null) 1f else 0.55f)) {
                Box(
                    Modifier.size(48.dp).brutal(MaterialTheme.shapes.small, Color.White, Malaysia.Ink, shadow = 0.dp),
                    contentAlignment = Alignment.Center,
                ) { Icon(icon, null, tint = Malaysia.Ink, modifier = Modifier.size(26.dp)) }
                Spacer(Modifier.weight(1f))
                Text(title, style = MaterialTheme.typography.titleMedium, color = Malaysia.Ink)
                Text(body, style = MaterialTheme.typography.bodyMedium, color = Malaysia.Ink.copy(alpha = 0.8f), maxLines = 2)
            }
            if (badge != null) {
                Box(Modifier.align(Alignment.TopEnd)) { Pill(badge, Color.White) }
            }
        }
    }
}
