package com.aisendiri.shared.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aisendiri.shared.device.Compatibility
import com.aisendiri.shared.i18n.LocalStrings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScaffold(
    title: String?,
    onBack: (() -> Unit)?,
    actions: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = {
            if (title != null || onBack != null) {
                TopAppBar(
                    title = { Text(title ?: "") },
                    navigationIcon = {
                        if (onBack != null) {
                            TextButton(onClick = onBack) { Text("‹ " + LocalStrings.current.back) }
                        }
                    },
                    actions = { actions() },
                )
            }
        },
        bottomBar = bottomBar,
        content = content,
    )
}

/** A centered, scrollable page with a big primary action at the bottom. */
@Composable
fun StepPage(
    padding: PaddingValues,
    emoji: String,
    title: String,
    body: String?,
    primary: Pair<String, () -> Unit>?,
    secondary: Pair<String, () -> Unit>? = null,
    content: @Composable () -> Unit = {},
) {
    Column(
        Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            Text(emoji, style = MaterialTheme.typography.displayLarge)
            Text(title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            if (body != null) {
                Text(
                    body,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            content()
        }
        Spacer(Modifier.padding(4.dp))
        if (primary != null) BigButton(primary.first, primary.second)
        if (secondary != null) {
            OutlinedButton(
                onClick = secondary.second,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(top = 8.dp),
            ) { Text(secondary.first) }
        }
    }
}

@Composable
fun BigButton(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

@Composable
fun InfoRow(label: String, value: String, ok: Boolean? = null) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Text(value, style = MaterialTheme.typography.bodyLarge)
        if (ok != null) {
            Spacer(Modifier.width(8.dp))
            Text(if (ok) "✅" else "⚠️")
        }
    }
}

@Composable
fun SectionCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(16.dp)) { content() }
    }
}

@Composable
fun Badge(text: String, highlight: Boolean = false) {
    Surface(
        color = if (highlight) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer,
        shape = MaterialTheme.shapes.small,
    ) {
        Box(Modifier.padding(horizontal = 8.dp, vertical = 2.dp)) {
            Text(text, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
fun compatibilityLabel(c: Compatibility): String {
    val s = LocalStrings.current
    return when (c) {
        Compatibility.WORKS_WELL -> "✅ " + s.compatWorksWell
        Compatibility.MAY_BE_SLOW -> "🐢 " + s.compatMaySlow
        Compatibility.TOO_HEAVY -> "⛔ " + s.compatTooHeavy
        Compatibility.NOT_ENOUGH_STORAGE -> "💾 " + s.compatNoStorage
        Compatibility.UNSUPPORTED_PHONE -> "⛔ " + s.compatUnsupported
    }
}

fun formatBytes(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1000) {
        val tenths = kotlin.math.round(mb / 1024.0 * 10).toLong()
        "${tenths / 10}.${tenths % 10} GB"
    } else {
        "${kotlin.math.round(mb).toLong()} MB"
    }
}
