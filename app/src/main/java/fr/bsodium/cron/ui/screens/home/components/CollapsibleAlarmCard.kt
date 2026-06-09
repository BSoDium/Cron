package fr.bsodium.cron.ui.screens.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import fr.bsodium.cron.session.model.SleepSegment
import fr.bsodium.cron.ui.theme.CronTheme
import fr.bsodium.cron.ui.theme.CronTypography
import fr.bsodium.cron.ui.theme.Radius
import fr.bsodium.cron.ui.theme.Spacing
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.math.roundToInt

// Max stroke (local 76sp space) overlaid on the collapsed digits to fake weight as they shrink.
// Conservative default — bump for bolder collapsed digits.
private val MAX_CLOCK_STROKE = 1.5.dp

/** Collapsed alarm-bar height: a perfect pill (= 2 × the corner radius). Shared with HomeContent's
 *  collapse-range math so the card shrinks at exactly finger speed (range == full − bar height). */
internal val ALARM_BAR_HEIGHT = Radius.xl * 2

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
    sessionDate: LocalDate?,
    sleepDurationLabel: String?,
    sleepSegments: List<SleepSegment>,
    collapseFraction: Float,
    onFullHeight: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val f = collapseFraction.coerceIn(0f, 1f)
    val timing = rememberAlarmTiming(alarmTime, sessionDate)
    val onCard = MaterialTheme.colorScheme.onPrimary
    // Only an upcoming alarm reads in full; no alarm and a passed alarm both render the grayed onset look.
    val upcoming = timing is AlarmTiming.Upcoming
    val digitColor = if (upcoming) onCard else onCard.copy(alpha = 0.30f)
    val countdownColor = if (upcoming) onCard.copy(alpha = 0.7f) else onCard.copy(alpha = 0.30f)
    // One reveal, shared by the moving clock + countdown (the hidden time row in extras keeps its own
    // deterministic copy, never drawn).
    val progress = rememberLcdRevealProgress(alarmTime)
    val ink = rememberLcdInkMetrics()
    val dateStyle = CronTypography.dateLabel.copy(fontSize = 28.sp, lineHeight = 28.sp)

    AlarmShell(
        modifier = modifier,
        shape = RoundedCornerShape(Radius.xl),
    ) {
        SubcomposeLayout(modifier = Modifier.clipToBounds()) { constraints ->
            val cWrap = constraints.copy(minWidth = 0, minHeight = 0)
            // Extras fills the full card width (its sleep tile spans it) so the layout width — and the
            // right-aligned countdown — track the real card width, not the wrapped content width.
            val cFill = if (constraints.hasBoundedWidth) constraints.copy(minWidth = constraints.maxWidth, minHeight = 0) else cWrap
            val extrasAlpha = (1f - f * 1.6f).coerceIn(0f, 1f)

            // Full expanded layout with its time row and date hidden (still measured) → single source of
            // geometry; those are drawn as moving copies on top. The badge isn't in the expanded card.
            val extras = subcompose("extras") {
                AlarmCardContent(dateLabel, alarmTime, timing, sleepDurationLabel, sleepSegments, timeRowAlpha = 0f, dateAlpha = 0f)
            }.first().measure(cFill)
            onFullHeight(extras.height)
            // Date mover — the same AlignedFirstGlyph as extras, placed so it can slide up out the top.
            val date = subcompose("date") {
                AlignedFirstGlyph(dateLabel.ifBlank { "—" }, color = onCard, style = dateStyle)
            }.first().measure(cWrap)
            // Collapsed bar is a perfect pill: height = 2 × Radius.xl, so the constant Radius.xl corners round it fully.
            val barHeight = ALARM_BAR_HEIGHT.roundToPx()
            // Inset > a tight fit so the shrunken digits gain a little spacing above and below in the pill.
            val innerInset = Spacing.md.roundToPx()
            // Centre on the digit INK, not the line box (Major Mono digits have no descenders → sit high).
            // Derived from the font's measured line box so the scale is known BEFORE the clock is
            // subcomposed — the weight stroke is a draw param baked in at that point.
            val inkHeightPx = ink.heightFraction * ink.lineBoxPx
            val inkCenterPx = ink.centerFraction * ink.lineBoxPx
            val collapsedScale = ((barHeight - innerInset * 2) / inkHeightPx).coerceIn(0.2f, 1f)
            val clockScale = lerp(1f, collapsedScale, f)
            // Fake weight grows in proportion to the size DECREASE. Dividing the local stroke by the
            // glyph scale cancels the dilution from shrinking, so the RENDERED stroke tracks f directly.
            val strokePx = MAX_CLOCK_STROKE.toPx() * f / clockScale
            val clock = subcompose("clock") {
                LcdClock(alarmTime, progress, digitColor, strokeWidthPx = strokePx)
            }.first().measure(cWrap)
            // The remaining is the SAME CountdownStack as expanded (identical font/size/weight) — it just
            // moves and re-aligns its lines (left→right) via alignFraction; it never fades or resizes.
            val countdown = subcompose("countdown") {
                RemainingOrStatus(timing = timing, progress = progress, color = countdownColor, alignFraction = f)
            }.first().measure(cWrap)

            val w = extras.width
            val startPad = Spacing.xxl.roundToPx()
            val topPad = Spacing.xl.roundToPx()
            val endPad = Spacing.xxl.roundToPx()
            val cdGap = (Spacing.xs + Spacing.xxs).roundToPx()
            val height = lerp(extras.height, barHeight, f)

            val expandedClockY = topPad + date.height
            val expandedInkCenter = expandedClockY + inkCenterPx
            val pillCenter = barHeight / 2f

            // Countdown mover: expanded slot (right of the clock, matching the LcdTimeDisplay row) → pill-right, centred.
            val expandedCdX = startPad + clock.width + cdGap
            val expandedCdY = expandedClockY + cdGap
            val collapsedCdX = w - endPad - countdown.width
            val collapsedCdY = (barHeight - countdown.height) / 2f

            layout(w, height) {
                if (extrasAlpha > 0f) extras.placeWithLayer(0, 0) { alpha = extrasAlpha }
                // Date: sits where extras' (hidden) date is at f=0, then slides UP out the top and fades —
                // opposite the time, which rises from the bottom.
                if (extrasAlpha > 0f) {
                    date.placeWithLayer(startPad, topPad) {
                        translationY = -(topPad + date.height).toFloat() * f
                        alpha = extrasAlpha
                    }
                }
                // Clock: left-anchored at startPad in BOTH states (the collapsed time stays left-aligned);
                // pivot on the digit-ink centre so scaling + translation land the ink dead-centre in the pill.
                clock.placeWithLayer(startPad, expandedClockY) {
                    scaleX = clockScale
                    scaleY = clockScale
                    transformOrigin = TransformOrigin(0f, ink.centerFraction)
                    translationY = lerp(0f, pillCenter - expandedInkCenter, f)
                }
                // Countdown: identical typography in both states, just moves (no fade, no scale).
                countdown.place(
                    x = lerp(expandedCdX.toFloat(), collapsedCdX.toFloat(), f).roundToInt(),
                    y = lerp(expandedCdY.toFloat(), collapsedCdY, f).roundToInt(),
                )
            }
        }
    }
}

/** The collapsed state — the slim bar (alarm time + remaining). */
@Preview(showBackground = true, name = "Collapsed")
@Composable
private fun CollapsedAlarmCardPreview() {
    CronTheme {
        Box(modifier = Modifier.padding(Spacing.md)) {
            CollapsibleAlarmCard(
                dateLabel = "Saturday 6",
                alarmTime = LocalTime(10, 0),
                sessionDate = null,
                sleepDurationLabel = "7H 28M",
                sleepSegments = PREVIEW_SLEEP_SEGMENTS,
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
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            listOf(0f, 0.25f, 0.5f, 0.75f, 1f).forEach { frac ->
                CollapsibleAlarmCard(
                    dateLabel = "Saturday 6",
                    alarmTime = LocalTime(10, 0),
                    sessionDate = null,
                    sleepDurationLabel = "7H 28M",
                    sleepSegments = PREVIEW_SLEEP_SEGMENTS,
                    collapseFraction = frac,
                    onFullHeight = {},
                )
            }
        }
    }
}
