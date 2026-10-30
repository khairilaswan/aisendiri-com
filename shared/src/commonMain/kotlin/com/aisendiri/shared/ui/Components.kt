package com.aisendiri.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aisendiri.shared.device.Compatibility
import com.aisendiri.shared.i18n.LocalStrings

// ---------------------------------------------------------------------------
// Neo-brutalist building blocks: flat colour, thick ink outline, hard offset shadow.
// ---------------------------------------------------------------------------

val BorderWidth = 2.5.dp
val ShadowOffset = 4.dp

/** Flat fill, thick outline and a solid shadow offset down-right. */
fun Modifier.brutal(
    shape: Shape,
    color: Color,
    ink: Color,
    shadow: Dp = ShadowOffset,
): Modifier = this
    .drawBehind {
        if (shadow > 0.dp) {
            val o = shadow.toPx()
            val outline = shape.createOutline(size, layoutDirection, this)
            translate(o, o) { drawOutline(outline, ink) }
        }
    }
    .clip(shape)
    .background(color)
    .border(BorderWidth, ink, shape)

/** A pressable brutal block: the shadow collapses as it's pressed, like a physical key. */
@Composable
fun PressableBlock(
    onClick: () -> Unit,
    color: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = MaterialTheme.shapes.medium,
    disabledColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val travel = if (pressed && enabled) ShadowOffset - 1.dp else 0.dp
    Box(
        modifier
            .offset(travel, travel)
            .brutal(shape, if (enabled) color else disabledColor, ink, ShadowOffset - travel)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScaffold(
    title: String?,
    onBack: (() -> Unit)?,
    subtitle: String? = null,
    actions: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            if (title != null || onBack != null) {
                TopAppBar(
                    title = {
                        Column {
                            Text(title ?: "", style = MaterialTheme.typography.titleLarge)
                            if (subtitle != null) {
                                Text(subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    },
                    navigationIcon = {
                        if (onBack != null) {
                            SquareIconButton(AppIcons.Back, LocalStrings.current.back, onBack, Modifier.padding(start = 8.dp))
                        }
                    },
                    actions = { actions() },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                )
            }
        },
        bottomBar = bottomBar,
        content = content,
    )
}

/** A small square brutal button holding an icon, used in top bars. */
@Composable
fun SquareIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surface,
    enabled: Boolean = true,
) {
    PressableBlock(onClick, color, modifier.size(42.dp), enabled, MaterialTheme.shapes.small) {
        Icon(icon, description, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp))
    }
}

/** A chunky square badge with an icon. The hero of each step. */
@Composable
fun IconBadge(icon: ImageVector, size: Dp = 88.dp, warm: Boolean = false) {
    Box(
        Modifier.size(size).brutal(MaterialTheme.shapes.large, if (warm) Malaysia.Yellow else Malaysia.SkyTint, ink),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Malaysia.Ink, modifier = Modifier.size(size * 0.5f))
    }
}

/** A small outlined square holding an icon, for list rows and tiles. */
@Composable
fun IconTile(icon: ImageVector, container: Color = Malaysia.YellowTint, size: Dp = 44.dp) {
    Box(
        Modifier.size(size).brutal(MaterialTheme.shapes.small, container, ink, shadow = 0.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Malaysia.Ink, modifier = Modifier.size(size * 0.55f))
    }
}

/** A thin band of the flag's red and white stripes, used under hero headers. */
@Composable
fun StripeBand(modifier: Modifier = Modifier, stripes: Int = 14) {
    Row(modifier.fillMaxWidth().height(10.dp)) {
        repeat(stripes) { i ->
            Box(Modifier.weight(1f).fillMaxSize().background(if (i % 2 == 0) Malaysia.Red else Color.White))
        }
    }
}

/** A centered, scrollable page with a big primary action at the bottom. */
@Composable
fun StepPage(
    padding: PaddingValues,
    icon: ImageVector,
    title: String,
    body: String?,
    primary: Pair<String, () -> Unit>?,
    secondary: Pair<String, () -> Unit>? = null,
    warm: Boolean = false,
    content: @Composable () -> Unit = {},
) {
    Column(
        Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(end = ShadowOffset),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically),
        ) {
            IconBadge(icon, warm = warm)
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
        Spacer(Modifier.height(12.dp))
        if (primary != null) BigButton(primary.first, primary.second)
        if (secondary != null) {
            Spacer(Modifier.height(12.dp))
            SecondaryButton(secondary.first, secondary.second)
        }
    }
}

@Composable
fun BigButton(text: String, onClick: () -> Unit, enabled: Boolean = true, color: Color = Malaysia.Yellow) {
    PressableBlock(onClick, color, Modifier.fillMaxWidth().padding(end = ShadowOffset).heightIn(min = 58.dp), enabled) {
        Text(text.uppercase(), style = MaterialTheme.typography.labelLarge, color = Malaysia.Ink, modifier = Modifier.padding(12.dp))
    }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier.fillMaxWidth(), enabled: Boolean = true) {
    PressableBlock(onClick, MaterialTheme.colorScheme.surface, modifier.padding(end = ShadowOffset).heightIn(min = 50.dp), enabled) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
    }
}

@Composable
fun InfoRow(icon: ImageVector, label: String, value: String, ok: Boolean? = null) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        IconTile(icon, size = 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium)
        }
        if (ok != null) {
            Icon(
                if (ok) AppIcons.CheckCircle else AppIcons.Warning,
                contentDescription = null,
                tint = if (ok) Malaysia.Green else Malaysia.Red,
            )
        }
    }
}

/** A flat card with a thick outline and hard shadow. The basic container for grouped content. */
@Composable
fun SectionCard(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.surface, content: @Composable () -> Unit) {
    Box(modifier.fillMaxWidth().padding(end = ShadowOffset, bottom = ShadowOffset)) {
        Column(Modifier.fillMaxWidth().brutal(MaterialTheme.shapes.medium, color, ink).padding(16.dp)) { content() }
    }
}

/** A tappable settings-style row: icon, title, optional subtitle, and a chevron or custom trailing part. */
@Composable
fun ListRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {
        if (onClick != null) Icon(AppIcons.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
    },
) {
    Row(
        Modifier.fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(icon)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        trailing()
    }
}

@Composable
fun Pill(text: String, container: Color = Malaysia.SkyTint) {
    Box(Modifier.brutal(CircleShape, container, ink, shadow = 0.dp).padding(horizontal = 10.dp, vertical = 3.dp)) {
        Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = Malaysia.Ink)
    }
}

@Composable
fun Badge(text: String, highlight: Boolean = false) {
    Pill(text, if (highlight) Malaysia.Yellow else Malaysia.SkyTint)
}

/** A square chip and label saying how well an AI suits this phone. */
@Composable
fun CompatibilityLine(c: Compatibility, modifier: Modifier = Modifier) {
    val s = LocalStrings.current
    val (label, color) = when (c) {
        Compatibility.WORKS_WELL -> s.compatWorksWell to Malaysia.Green
        Compatibility.MAY_BE_SLOW -> s.compatMaySlow to Malaysia.Yellow
        Compatibility.TOO_HEAVY -> s.compatTooHeavy to Malaysia.Red
        Compatibility.NOT_ENOUGH_STORAGE -> s.compatNoStorage to Malaysia.Red
        Compatibility.UNSUPPORTED_PHONE -> s.compatUnsupported to Malaysia.Red
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(14.dp).brutal(MaterialTheme.shapes.extraSmall, color, ink, shadow = 0.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

/** A thick outlined progress bar with a yellow fill. Null shows a striped "working" bar. */
@Composable
fun BrutalProgress(fraction: Float?, modifier: Modifier = Modifier) {
    Box(
        modifier.fillMaxWidth().padding(vertical = 12.dp).height(22.dp)
            .brutal(MaterialTheme.shapes.small, MaterialTheme.colorScheme.surfaceVariant, ink, shadow = 0.dp),
    ) {
        if (fraction != null) {
            Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().background(Malaysia.Yellow))
        } else {
            androidx.compose.material3.LinearProgressIndicator(
                Modifier.fillMaxSize(),
                color = Malaysia.Yellow,
                trackColor = Color.Transparent,
            )
        }
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
