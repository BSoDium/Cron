package fr.bsodium.cron.ui.screens.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import fr.bsodium.cron.ui.theme.CronTheme
import fr.bsodium.cron.ui.theme.CronTypography
import fr.bsodium.cron.ui.theme.ExpressiveCondensedFontFamily
import fr.bsodium.cron.ui.theme.Spacing
import fr.bsodium.cron.ui.theme.TightTextStyle
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.time.Duration

internal data class HoursMinutes(val hours: Long, val minutes: Long)

/**
 * Two-line LCD stack ("8H" / "12M") showing time remaining until the alarm; dim placeholders when no
 * alarm is set. [alignFraction] slides each line horizontally inside the stack's width — 0 = left
 * (the expanded card), 1 = right (the collapsed pill, so the shorter line hugs the pill's right edge).
 */
private val FIRES_IN_STYLE = TightTextStyle.copy(
    fontFamily = ExpressiveCondensedFontFamily,
    fontWeight = FontWeight.Medium,
    fontSize = 13.sp,
    lineHeight = 13.sp,
)

@Composable
internal fun CountdownStack(
    countdown: HoursMinutes?,
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
    alignFraction: Float = 0f,
    showLabel: Boolean = true,
    labelAlpha: Float = 1f,
) {
    // No alarm → a grayed "00H/00M" placeholder, mirroring the dimmed "00:00" digits.
    val (top, bottom) = if (countdown == null) "00H" to "00M"
    else String.format(Locale.US, "%dH", (countdown.hours * progress).roundToInt()) to
        String.format(Locale.US, "%dM", (countdown.minutes * progress).roundToInt())
    Column(modifier = modifier) {
        TwoLineLcdStack(top = top, bottom = bottom, color = color, alignFraction = alignFraction)
        if (showLabel) {
            Text(
                text = "fires in",
                color = color,
                style = FIRES_IN_STYLE,
                modifier = Modifier.graphicsLayer { alpha = labelAlpha },
            )
        }
    }
}

/** The live countdown, or a grayed "00H/00M" placeholder. A passed alarm reads as onset here — the
 *  "out of date / no run yet" message lives below the card on the resting screen, not in this slot. */
@Composable
internal fun RemainingOrStatus(
    timing: AlarmTiming,
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
    alignFraction: Float = 0f,
    showLabel: Boolean = true,
    labelAlpha: Float = 1f,
) {
    when (timing) {
        is AlarmTiming.Upcoming -> CountdownStack(timing.remaining, progress, color, modifier, alignFraction, showLabel, labelAlpha)
        AlarmTiming.None, AlarmTiming.Past -> CountdownStack(null, progress, color, modifier, alignFraction, showLabel, labelAlpha)
    }
}

/** Two short LCD lines stacked by baseline pitch, optionally slid left→right by [alignFraction]
 *  (0 = left, expanded card; 1 = right, collapsed pill). */
@Composable
private fun TwoLineLcdStack(
    top: String,
    bottom: String,
    color: Color,
    modifier: Modifier = Modifier,
    alignFraction: Float = 0f,
) {
    // Space Grotesk (legible, unlike Major Mono's art-deco H/M) — the shared compact-stack role.
    val smallLcd = CronTypography.lcdStack
    val align = alignFraction.coerceIn(0f, 1f)
    Layout(
        modifier = modifier,
        content = {
            Text(text = top, color = color, style = smallLcd, maxLines = 1, softWrap = false)
            Text(text = bottom, color = color, style = smallLcd, maxLines = 1, softWrap = false)
        },
    ) { measurables, constraints ->
        val (line0, line1) = measurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
        // Stack by BASELINE pitch (= the style's lineHeight), reproducing a single 2-line Text's leading
        // rather than the sum of trimmed line heights (which spaced the lines too far apart).
        val pitch = smallLcd.lineHeight.roundToPx()
        val b0 = line0[FirstBaseline]
        val b1 = line1[FirstBaseline]
        val y1 = if (b0 == AlignmentLine.Unspecified || b1 == AlignmentLine.Unspecified) line0.height
        else b0 + pitch - b1
        val width = maxOf(line0.width, line1.width)
        val height = y1 + line1.height
        layout(width, height) {
            line0.place(x = ((width - line0.width) * align).roundToInt(), y = 0)
            line1.place(x = ((width - line1.width) * align).roundToInt(), y = y1)
        }
    }
}

/** Where the planned wake moment sits relative to now, anchored to the session's morning date. */
internal sealed interface AlarmTiming {
    /** No alarm set. */
    data object None : AlarmTiming
    /** The wake moment is still ahead; [remaining] until it. */
    data class Upcoming(val remaining: HoursMinutes) : AlarmTiming
    /** `sessionDate + alarmTime` has already elapsed — the plan is stale and needs a replan. */
    data object Past : AlarmTiming
}

/**
 * Classifies [alarmTime] against now, anchored to [sessionDate] (the morning the plan targets) so a time
 * that already passed today reads as [AlarmTiming.Past] rather than silently rolling forward to tomorrow.
 * Falls back to the next-occurrence rule when no date is known.
 */
internal fun computeAlarmTiming(alarmTime: LocalTime?, sessionDate: LocalDate?): AlarmTiming {
    if (alarmTime == null) return AlarmTiming.None
    val tz = TimeZone.currentSystemDefault()
    val now = Clock.System.now()
    val target = if (sessionDate != null) {
        LocalDateTime(sessionDate, alarmTime).toInstant(tz)
    } else {
        val nowLocal = now.toLocalDateTime(tz)
        val date = if (alarmTime > nowLocal.time) nowLocal.date else nowLocal.date.plus(1, DateTimeUnit.DAY)
        LocalDateTime(date, alarmTime).toInstant(tz)
    }
    val remaining = target - now
    if (remaining <= Duration.ZERO) return AlarmTiming.Past
    val totalMinutes = remaining.inWholeMinutes
    return AlarmTiming.Upcoming(HoursMinutes(hours = totalMinutes / 60, minutes = totalMinutes % 60))
}

/** [computeAlarmTiming] re-evaluated each minute so the card flips to stale (and the countdown ticks)
 *  without a manual refresh. */
@Composable
internal fun rememberAlarmTiming(alarmTime: LocalTime?, sessionDate: LocalDate?): AlarmTiming {
    val timing by produceState(computeAlarmTiming(alarmTime, sessionDate), alarmTime, sessionDate) {
        while (true) {
            value = computeAlarmTiming(alarmTime, sessionDate)
            delay(60_000)
        }
    }
    return timing
}

@Preview(showBackground = true, name = "Countdown — left vs right align")
@Composable
private fun CountdownStackPreview() {
    CronTheme {
        Row(
            modifier = Modifier.padding(Spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xxl),
        ) {
            CountdownStack(countdown = HoursMinutes(13, 9), progress = 1f, color = MaterialTheme.colorScheme.onSurface, alignFraction = 0f)
            CountdownStack(countdown = HoursMinutes(13, 9), progress = 1f, color = MaterialTheme.colorScheme.onSurface, alignFraction = 1f)
        }
    }
}
