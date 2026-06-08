package fr.bsodium.cron.ui.screens.home.components

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.bsodium.cron.ui.theme.CronTheme
import fr.bsodium.cron.ui.theme.CronTypography
import fr.bsodium.cron.ui.theme.Spacing

// Line-box ÷ font-size for grotesque sans faces — used until the real typeface resolves.
private const val LINE_RATIO_FALLBACK = 1.17f
// M3 Switch track height — the first-frame target before the toggle reports its measured size.
private val DEFAULT_SWITCH_HEIGHT = 32.dp

/**
 * Top-of-home greeting: a muted prefix over the user's name, with the auto-alarms switch sharing the
 * **name's line** so the two fill one band. The name is sized so its **line box** equals the measured
 * switch height — name and switch read as one rectangle. With no name, the prefix is that single line.
 *
 *   Good evening,
 *   **Elliot**            [switch]
 */
@Composable
fun HomeGreetingRow(
    prefix: String,
    name: String?,
    autoAlarmsEnabled: Boolean,
    onAutoAlarmsChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    var switchHeight by remember { mutableStateOf(DEFAULT_SWITCH_HEIGHT) }
    val nameStyle = rememberNameStyle(switchHeight)
    Column(modifier = modifier.fillMaxWidth()) {
        if (!name.isNullOrBlank()) {
            Text(
                text = "$prefix,",
                style = CronTypography.greetingPrefix,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(
                text = name?.takeIf { it.isNotBlank() } ?: prefix,
                style = nameStyle,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            AutoAlarmToggle(
                enabled = autoAlarmsEnabled,
                onChange = onAutoAlarmsChange,
                modifier = Modifier.onSizeChanged { if (it.height > 0) switchHeight = with(density) { it.height.toDp() } },
            )
        }
    }
}

/**
 * The name text style sized so its **line box** equals [target] (the switch height), glyphs centred and
 * filling it. Measures the resolved typeface's line-box ÷ size ratio; reading the resolver's `State.value`
 * in composition lets it refresh when a downloadable face loads.
 */
@Composable
private fun rememberNameStyle(target: Dp): TextStyle {
    val resolver = LocalFontFamilyResolver.current
    val density = LocalDensity.current
    val base = CronTypography.greetingName
    val typeface = base.fontFamily?.let {
        resolver.resolve(it, base.fontWeight ?: FontWeight.Normal, FontStyle.Normal, FontSynthesis.None).value as? Typeface
    }
    return remember(typeface, density.density, target) {
        val targetPx = with(density) { target.toPx() }
        val ratio = runCatching {
            val tf = typeface ?: return@runCatching LINE_RATIO_FALLBACK
            val paint = Paint().apply { this.typeface = tf; textSize = 200f; isAntiAlias = true }
            val fm = paint.fontMetrics
            ((fm.descent - fm.ascent) / 200f).takeIf { it > 0f } ?: LINE_RATIO_FALLBACK
        }.getOrDefault(LINE_RATIO_FALLBACK)
        base.copy(
            fontSize = with(density) { (targetPx / ratio).toSp() },
            lineHeight = with(density) { targetPx.toSp() },
            platformStyle = PlatformTextStyle(includeFontPadding = false),
            lineHeightStyle = LineHeightStyle(
                alignment = LineHeightStyle.Alignment.Center,
                trim = LineHeightStyle.Trim.Both,
            ),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun GreetingHeaderPreview() {
    CronTheme {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
            HomeGreetingRow(prefix = "Good morning", name = "Elliot", autoAlarmsEnabled = true, onAutoAlarmsChange = {})
            HomeGreetingRow(prefix = "Good evening", name = "Maximilian-Alexander", autoAlarmsEnabled = false, onAutoAlarmsChange = {})
        }
    }
}
