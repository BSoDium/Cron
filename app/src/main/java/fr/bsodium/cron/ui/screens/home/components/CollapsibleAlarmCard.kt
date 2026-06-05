package fr.bsodium.cron.ui.screens.home.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import fr.bsodium.cron.session.model.SleepSegment
import fr.bsodium.cron.ui.theme.CodeFontFamily
import fr.bsodium.cron.ui.theme.CronTheme
import fr.bsodium.cron.ui.theme.CronTypography
import fr.bsodium.cron.ui.theme.Radius
import fr.bsodium.cron.ui.theme.Spacing
import fr.bsodium.cron.ui.theme.TightTextStyle
import kotlinx.coroutines.delay
import kotlinx.datetime.LocalTime
import kotlin.math.roundToInt

// Max stroke (local 76sp space) overlaid on the collapsed digits to fake weight as they shrink.
// Conservative default — bump for bolder collapsed digits.
private val MAX_CLOCK_STROKE = 2.5.dp

/**
 * The alarm card that collapses on scroll. Rather than crossfade two layouts, the **"HH:MM" clock**
 * is a single element that translates + scales (left-edge anchored, since both states share the same
 * left inset) from its expanded slot (76sp, under the date) to a perfect-pill bar (fit-to-height,
 * centered) as [collapseFraction] goes 0→1, its strokes thickening as it shrinks. The expanded extras
 * (date, countdown, sleep) fade out; the inline remaining slides in. [onFullHeight] reports the
 * expanded height so the caller reserves a stable slot.
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
    val onCard = MaterialTheme.colorScheme.onPrimary
    val digitColor = if (alarmTime == null) onCard.copy(alpha = 0.30f) else onCard
    // One reveal, shared by the moving clock here (the invisible clock inside the extras layer keeps
    // its own deterministic copy, never drawn).
    val progress = rememberLcdRevealProgress(alarmTime)
    val dateStyle = CronTypography.dateLabel.copy(fontSize = 28.sp, lineHeight = 28.sp)

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
        shape = RoundedCornerShape(Radius.xl),
    ) {
        SubcomposeLayout(modifier = Modifier.clipToBounds()) { constraints ->
            val cWrap = constraints.copy(minWidth = 0, minHeight = 0)
            // Extras fills the full card width (its sleep tile spans it) so the layout width — and the
            // right-aligned remaining — track the real card width, not the wrapped content width.
            val cFill = if (constraints.hasBoundedWidth) constraints.copy(minWidth = constraints.maxWidth, minHeight = 0) else cWrap
            val extrasAlpha = (1f - f * 1.6f).coerceIn(0f, 1f)
            val remainingAlpha = ((f - 0.4f) / 0.6f).coerceIn(0f, 1f)

            // Full expanded layout with its clock hidden (still measured) → single source of geometry.
            val extras = subcompose("extras") {
                AlarmCardContent(dateLabel, alarmTime, sleepDurationLabel, sleepSegments, clockAlpha = 0f)
            }.first().measure(cFill)
            onFullHeight(extras.height)
            // Date proxy — height only — to place the moving clock at the expanded clock-Y.
            val date = subcompose("date") {
                Text(dateLabel.ifBlank { "—" }, style = dateStyle, maxLines = 1)
            }.first().measure(cWrap)
            // Stroke grows with collapse so the shrinking digits keep weight (local px; scaled with the glyph).
            val strokePx = MAX_CLOCK_STROKE.toPx() * f
            val clock = subcompose("clock") {
                LcdClock(alarmTime, progress, digitColor, strokeWidthPx = strokePx)
            }.first().measure(cWrap)
            val remaining = subcompose("remaining") { RemainingText(alarmTime, onCard) }.first().measure(cWrap)

            val w = extras.width
            val startPad = Spacing.xxl.roundToPx()
            val topPad = Spacing.xl.roundToPx()
            val endPad = Spacing.xxl.roundToPx()
            // Collapsed bar is a perfect pill: height = 2 × Radius.xl, so the constant Radius.xl corners
            // round it fully. The clock scales to FIT that height (bigger than a literal point-size shrink).
            val barHeight = (Radius.xl * 2).roundToPx()
            val innerInset = Spacing.xs.roundToPx()
            val collapsedScale = ((barHeight - innerInset * 2).toFloat() / clock.height).coerceIn(0.2f, 1f)
            val height = lerp(extras.height, barHeight, f)

            val expandedClockY = topPad + date.height
            val expandedCenter = expandedClockY + clock.height / 2f
            val collapsedCenter = barHeight / 2f

            layout(w, height) {
                if (extrasAlpha > 0f) extras.placeWithLayer(0, 0) { alpha = extrasAlpha }
                // Left-edge anchored; vertical translate + uniform scale about the left-center origin.
                clock.placeWithLayer(startPad, expandedClockY) {
                    val s = lerp(1f, collapsedScale, f)
                    scaleX = s
                    scaleY = s
                    transformOrigin = TransformOrigin(0f, 0.5f)
                    translationY = lerp(0f, collapsedCenter - expandedCenter, f)
                }
                if (remainingAlpha > 0f) {
                    val rx = w - endPad - remaining.width
                    // Slide it down from near the expanded countdown into the bar centre as it fades in.
                    val ry = lerp(expandedClockY.toFloat(), (barHeight - remaining.height) / 2f, f).roundToInt()
                    remaining.placeWithLayer(rx, ry) { alpha = remainingAlpha }
                }
            }
        }
    }
}

/** "21H 41M" remaining, ticking once a minute. Used as the collapsed bar's right-hand label. */
@Composable
private fun RemainingText(alarmTime: LocalTime?, color: Color, modifier: Modifier = Modifier) {
    val remaining by produceState(remainingLabel(alarmTime), alarmTime) {
        while (true) {
            value = remainingLabel(alarmTime)
            delay(60_000)
        }
    }
    Text(
        text = remaining,
        color = color.copy(alpha = 0.85f),
        style = TightTextStyle.copy(fontFamily = CodeFontFamily, fontSize = 16.sp, lineHeight = 16.sp),
        maxLines = 1,
        softWrap = false,
        modifier = modifier,
    )
}

/** The collapsed state — the slim bar (alarm time + remaining). */
@Preview(showBackground = true, name = "Collapsed")
@Composable
private fun CollapsedAlarmCardPreview() {
    CronTheme {
        androidx.compose.foundation.layout.Box(modifier = Modifier.padding(Spacing.md)) {
            CollapsibleAlarmCard(
                dateLabel = "Saturday 6",
                alarmTime = LocalTime(10, 0),
                sleepDurationLabel = "7H 28M",
                sleepSegments = emptyList(),
                collapseFraction = 1f,
                onFullHeight = {},
            )
        }
    }
}

/** The collapse scrub — full → bar across representative fractions. */
@Preview(showBackground = true, name = "Collapse scrub")
@Composable
private fun CollapsibleAlarmCardPreview() {
    CronTheme {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Spacing.md),
        ) {
            listOf(0f, 0.25f, 0.5f, 0.75f, 1f).forEach { frac ->
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
