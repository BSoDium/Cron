package fr.bsodium.cron.ui.screens.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import fr.bsodium.cron.ui.theme.CronTheme
import fr.bsodium.cron.ui.theme.DisplayFontFamily
import fr.bsodium.cron.ui.theme.Spacing
import fr.bsodium.cron.ui.theme.TightTextStyle
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import java.util.Locale
import kotlin.math.roundToInt

internal data class HoursMinutes(val hours: Long, val minutes: Long)

/**
 * Two-line LCD stack ("8H" / "12M") showing time remaining until the alarm; dim placeholders when no
 * alarm is set. [alignFraction] slides each line horizontally inside the stack's width — 0 = left
 * (the expanded card), 1 = right (the collapsed pill, so the shorter line hugs the pill's right edge).
 */
@Composable
internal fun CountdownStack(
    countdown: HoursMinutes?,
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
    alignFraction: Float = 0f,
) {
    // Space Grotesk (legible, unlike Major Mono's art-deco H/M); lineHeight < fontSize tightens the
    // H↔M leading so the stack is compact and gains breathing room when centred in the collapsed pill.
    val smallLcd = TightTextStyle.copy(
        fontFamily = DisplayFontFamily,
        fontSize = 24.sp,
        lineHeight = 21.sp,
    )
    // No alarm → a grayed "00H/00M" placeholder, mirroring the dimmed "00:00" digits.
    val (top, bottom) = if (countdown == null) "00H" to "00M"
    else String.format(Locale.US, "%dH", (countdown.hours * progress).roundToInt()) to
        String.format(Locale.US, "%dM", (countdown.minutes * progress).roundToInt())
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
        val pitch = 21.sp.roundToPx()
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

/**
 * Distance from `now` to the next occurrence of [alarmTime] (today if it's
 * still ahead, otherwise tomorrow). Returns null when no alarm is set.
 */
internal fun computeCountdown(alarmTime: LocalTime?): HoursMinutes? {
    if (alarmTime == null) return null
    val tz = TimeZone.currentSystemDefault()
    val nowLocal = Clock.System.now().toLocalDateTime(tz)
    val targetDate = if (alarmTime > nowLocal.time) nowLocal.date else nowLocal.date.plus(1, DateTimeUnit.DAY)
    val targetInstant = LocalDateTime(targetDate, alarmTime).toInstant(tz)
    val remaining = targetInstant - Clock.System.now()
    val totalMinutes = remaining.inWholeMinutes.coerceAtLeast(0)
    return HoursMinutes(hours = totalMinutes / 60, minutes = totalMinutes % 60)
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
