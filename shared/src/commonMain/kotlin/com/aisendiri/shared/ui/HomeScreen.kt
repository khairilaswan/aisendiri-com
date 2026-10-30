@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.aisendiri.shared.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import com.aisendiri.shared.i18n.LocalStrings

@Composable
fun HomeScreen(c: AppController) {
    val s = LocalStrings.current
    val settings by c.settings.collectAsState()
    // Re-evaluated when the active AI or downloaded files change.
    val active = remember(settings.activeModelId, c.modelFilesVersion) { c.activeModel() }
    AppScaffold(title = s.appName, onBack = null) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(s.homeGreeting, style = MaterialTheme.typography.headlineMedium)
            if (active != null) {
                Badge("🟢 " + s.homeStatusReady + " · " + s.tierName(active.tier))
            } else {
                SectionCard {
                    Text(s.homeStatusNoAi, style = MaterialTheme.typography.titleMedium)
                    BigButton(s.setUpAi, { c.startPhoneCheck() })
                }
            }
            HomeCard("💬", s.homeChatTitle, s.homeChatBody, enabled = active != null) { c.openChat(null) }
            HomeCard("🎙️", s.homeTalkTitle, s.homeTalkBody, enabled = false, badge = s.comingSoon) {}
            HomeCard("📷", s.homePhotoTitle, s.homePhotoBody, enabled = false, badge = s.comingSoon) {}
            HomeCard("🎨", s.homeImageTitle, s.homeImageBody, enabled = false, badge = s.comingSoon) {}
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { c.loadHistory(); c.navigate(Screen.History) },
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                ) { Text("🕘 " + s.chatHistory) }
                OutlinedButton(
                    onClick = { c.navigate(Screen.Settings) },
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                ) { Text("⚙️ " + s.settings) }
            }
        }
    }
}

@Composable
private fun HomeCard(
    emoji: String,
    title: String,
    body: String,
    enabled: Boolean,
    badge: String? = null,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            Modifier.padding(20.dp).alpha(if (enabled || badge == null) 1f else 0.6f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(emoji, style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(end = 16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(body, style = MaterialTheme.typography.bodyMedium)
            }
            if (badge != null) Badge(badge, highlight = true)
        }
    }
}
