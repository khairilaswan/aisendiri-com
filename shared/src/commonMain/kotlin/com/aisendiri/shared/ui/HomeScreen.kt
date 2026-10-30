@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.aisendiri.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
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
        // Hero header
        Box(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
                .background(brandGradient)
                .statusBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 28.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(AppIcons.Sparkle, size = 40.dp, warm = true)
                    Spacer(Modifier.width(10.dp))
                    Text(s.appName, style = MaterialTheme.typography.titleLarge, color = Color.White, modifier = Modifier.weight(1f))
                    Box(
                        Modifier.size(44.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f))
                            .clickable { c.navigate(Screen.Settings) },
                        contentAlignment = Alignment.Center,
                    ) { Icon(AppIcons.Settings, s.settings, tint = Color.White) }
                }
                Text(s.homeGreeting, style = MaterialTheme.typography.headlineMedium, color = Color.White)
                if (active != null) {
                    Surface(color = Color.White.copy(alpha = 0.18f), contentColor = Color.White, shape = CircleShape) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF7CF5C0)))
                            Spacer(Modifier.width(8.dp))
                            Text(s.homeStatusReady + " · " + s.tierName(active.tier), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }

        Column(
            Modifier.padding(16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (active == null) {
                SectionCard {
                    Text(s.homeStatusNoAi, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.size(12.dp))
                    BigButton(s.setUpAi, { c.startPhoneCheck() })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FeatureTile(AppIcons.Chat, s.homeChatTitle, s.homeChatBody, null, highlighted = true, enabled = active != null, Modifier.weight(1f)) {
                    c.openChat(null)
                }
                FeatureTile(AppIcons.Mic, s.homeTalkTitle, s.homeTalkBody, s.comingSoon, highlighted = false, enabled = false, Modifier.weight(1f)) {}
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FeatureTile(AppIcons.Camera, s.homePhotoTitle, s.homePhotoBody, s.comingSoon, highlighted = false, enabled = false, Modifier.weight(1f)) {}
                FeatureTile(AppIcons.Image, s.homeImageTitle, s.homeImageBody, s.comingSoon, highlighted = false, enabled = false, Modifier.weight(1f)) {}
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
    highlighted: Boolean,
    enabled: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val shape = MaterialTheme.shapes.large
    val base = modifier.aspectRatio(0.92f).clip(shape)
    val background = if (highlighted && enabled) {
        base.background(brandGradient)
    } else {
        base.background(MaterialTheme.colorScheme.surface)
    }
    val fg = if (highlighted && enabled) Color.White else MaterialTheme.colorScheme.onSurface
    Box(background.clickable(enabled = enabled, onClick = onClick).padding(16.dp)) {
        Column(Modifier.fillMaxSize().alpha(if (enabled || highlighted) 1f else 0.6f)) {
            if (highlighted && enabled) {
                Box(Modifier.size(48.dp).clip(MaterialTheme.shapes.small).background(Color.White.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = Color.White, modifier = Modifier.size(26.dp))
                }
            } else {
                IconTile(icon, size = 48.dp)
            }
            Spacer(Modifier.weight(1f))
            Text(title, style = MaterialTheme.typography.titleMedium, color = fg)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = fg.copy(alpha = 0.8f), maxLines = 2)
        }
        if (badge != null) {
            Box(Modifier.align(Alignment.TopEnd)) { Badge(badge) }
        }
    }
}
