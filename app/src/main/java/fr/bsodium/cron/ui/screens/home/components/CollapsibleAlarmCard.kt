package fr.bsodium.cron.ui.screens.home.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import fr.bsodium.cron.session.model.SleepSegment
import fr.bsodium.cron.ui.theme.CodeFontFamily
import fr.bsodium.cron.ui.theme.CronTheme
import fr.bsodium.cron.ui.theme.LcdFontFamily
import fr.bsodium.cron.ui.theme.Radius
import fr.bsodium.cron.ui.theme.Spacing
import fr.bsodium.cron.ui.theme.TightTextStyle
import kotlinx.coroutines.delay
import kotlinx.datetime.LocalTime
import java.util.Locale

/**
 * The alarm card that collapses on scroll: at [collapseFraction] 0 it's the full [AlarmCardContent];
 * at 1 it's a slim [CollapsedAlarmBar] (alarm time + remaining). Both crossfade inside the shared
 * bold [AlarmShell], whose height lerps full→bar and whose corner radius eases xl→lg. The full
 * content is always composed (only alpha/height animate) so the LCD reveal never re-triggers on
 * scroll. [onFullHeight] reports the expanded height so the caller can reserve a stable slot.
 */
@Composable
internal fun CollapsibleAlarmCard(
    dateLabel: String,
    alarmTime: LocalTime?,
    sleepDurationLabel: String?,
    sleepSegments: List<SleepSegment>,
    collapseFraction: Float,
    onFullHeight: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val f = collapseFraction.coerceIn(0f, 1f)
    val radius = Radius.xl * (1f - f) + Radius.lg * f
    // Springy entrance: the card eases in with a small scale + fade the first time it appears.
    val entrance = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        entrance.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow))
    }
    AlarmShell(
        modifier = modifier.graphicsLayer {
            val e = entrance.value
            alpha = e
            scaleX = 0.97f + 0.03f * e
            scaleY = 0.97f + 0.03f * e
        },
        shape = RoundedCornerShape(radius),
    ) {
        SubcomposeLayout(modifier = Modifier.clipToBounds()) { constraints ->
            val c = constraints.copy(minHeight = 0)
            val full = subcompose("full") {
                AlarmCardContent(dateLabel, alarmTime, sleepDurationLabel, sleepSegments)
            }.first().measure(c)
            onFullHeight(full.height)
            val bar = subcompose("bar") { CollapsedAlarmBar(alarmTime) }.first().measure(c)
            val height = lerp(full.height, bar.height, f)
            // Offset alphas so it dissolves (full out by 0.62, bar in from 0.4) rather than double-exposes.
            val fullAlpha = (1f - f * 1.6f).coerceIn(0f, 1f)
            val barAlpha = ((f - 0.4f) / 0.6f).coerceIn(0f, 1f)
            layout(full.width, height) {
                if (fullAlpha > 0f) full.placeWithLayer(0, 0) { alpha = fullAlpha }
                if (barAlpha > 0f) bar.placeWithLayer(0, 0) { alpha = barAlpha }
            }
        }
    }
}

/** Slim collapsed state: hero alarm time on the left, time-remaining on the right, in `onPrimary`. */
@Composable
internal fun CollapsedAlarmBar(alarmTime: LocalTime?, modifier: Modifier = Modifier) {
    val onCard = MaterialTheme.colorScheme.onPrimary
    val remaining by produceState(remainingLabel(alarmTime), alarmTime) {
        while (true) {
            value = remainingLabel(alarmTime)
            delay(60_000)
        }
    }
    val timeText = alarmTime
        ?.let { String.format(Locale.US, "%02d:%02d", it.hour, it.minute) }
        ?: "--:--"
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.xxl, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = timeText,
            color = onCard,
            style = TightTextStyle.copy(fontFamily = LcdFontFamily, fontSize = 34.sp, lineHeight = 34.sp),
            maxLines = 1,
            softWrap = false,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = remaining,
            color = onCard.copy(alpha = 0.85f),
            style = TightTextStyle.copy(fontFamily = CodeFontFamily, fontSize = 16.sp, lineHeight = 16.sp),
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun CollapsibleAlarmCardPreview() {
    CronTheme {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Spacing.md),
        ) {
            listOf(0f, 0.5f, 1f).forEach { frac ->
                CollapsibleAlarmCard(
                    dateLabel = "Saturday 6",
                    alarmTime = LocalTime(10, 0),
                    sleepDurationLabel = "7H 28M",
                    sleepSegments = emptyList(),
                    collapseFraction = frac,
                    onFullHeight = {},
                )
            }
        }
    }
}
