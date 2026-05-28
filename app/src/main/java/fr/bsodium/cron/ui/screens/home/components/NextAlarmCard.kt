package fr.bsodium.cron.ui.screens.home.components

import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.bsodium.cron.session.model.SleepSegment
import fr.bsodium.cron.ui.components.PillBadge
import fr.bsodium.cron.ui.theme.CronTypography
import fr.bsodium.cron.ui.theme.LcdFontFamily
import fr.bsodium.cron.ui.theme.MonoFontFamily
import fr.bsodium.cron.ui.theme.Radius
import fr.bsodium.cron.ui.theme.Spacing
import fr.bsodium.cron.ui.theme.TightTextStyle
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import java.util.Locale

@Composable
fun NextAlarmCard(
    dateLabel: String,
    alarmTime: LocalTime?,
    sleepDurationLabel: String?,
    sleepSegments: List<SleepSegment>,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(Radius.xl),
        tonalElevation = 6.dp,
        shadowElevation = 0.dp,
    ) {
        Column(modifier = Modifier.padding(Spacing.xxl)) {
            AlignedFirstGlyph(
                text = dateLabel.ifBlank { "—" },
                color = MaterialTheme.colorScheme.onSurface,
                style = CronTypography.dateLabel,
            )
            LcdTimeDisplay(alarmTime = alarmTime)
            if (sleepSegments.isNotEmpty()) {
                Spacer(Modifier.height(Spacing.xl))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Sleep",
                        fontFamily = MonoFontFamily,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    if (sleepDurationLabel != null) {
                        PillBadge(
                            text = sleepDurationLabel,
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            textStyle = MaterialTheme.typography.labelMedium.copy(
                                fontFamily = MonoFontFamily,
                                fontWeight = FontWeight.Bold,
                            ),
                        )
                    }
                }
                Spacer(Modifier.height(Spacing.md + Spacing.xs))
                SleepTimeline(segments = sleepSegments)
            }
        }
    }
}

@Composable
private fun LcdTimeDisplay(alarmTime: LocalTime?, modifier: Modifier = Modifier) {
    // Tick once a minute so the countdown stays current without flickering.
    val countdown by produceState<HoursMinutes?>(initialValue = computeCountdown(alarmTime), alarmTime) {
        while (true) {
            value = computeCountdown(alarmTime)
            delay(60_000)
        }
    }
    val pending = alarmTime == null
    // Locale.US so the digits render as ASCII 0-9 on Arabic/Farsi/Bengali devices.
    val hh = alarmTime?.let { String.format(Locale.US, "%02d", it.hour) } ?: "00"
    val mm = alarmTime?.let { String.format(Locale.US, "%02d", it.minute) } ?: "00"
    val base = MaterialTheme.colorScheme.onSurface
    val digitColor = if (pending) base.copy(alpha = 0.22f) else base
    val countdownColor = if (pending) base.copy(alpha = 0.16f) else base.copy(alpha = 0.6f)

    val lcdStyle = TightTextStyle.copy(
        fontFamily = LcdFontFamily,
        fontSize = 76.sp,
        lineHeight = 76.sp,
    )

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.Top,
    ) {
        AlignedFirstGlyph(text = hh, color = digitColor, style = lcdStyle)
        ColonSeparator(
            color = digitColor,
            modifier = Modifier
                .align(Alignment.CenterVertically)
                .padding(horizontal = 8.dp),
        )
        Text(text = mm, color = digitColor, style = lcdStyle, maxLines = 1, softWrap = false)
        CountdownStack(
            countdown = countdown,
            color = countdownColor,
            modifier = Modifier.padding(start = 6.dp, top = 6.dp),
        )
    }
}

private data class HoursMinutes(val hours: Long, val minutes: Long)

/**
 * Two-line LCD stack ("8H" / "12M") showing time remaining until the alarm.
 * Renders dim placeholders when no alarm is set.
 */
@Composable
private fun CountdownStack(
    countdown: HoursMinutes?,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val smallLcd = TightTextStyle.copy(
        fontFamily = LcdFontFamily,
        fontSize = 24.sp,
        lineHeight = 26.sp,
    )
    val (top, bottom) = if (countdown == null) "——H" to "——M"
    else String.format(Locale.US, "%dH", countdown.hours) to
        String.format(Locale.US, "%dM", countdown.minutes)
    Column(modifier = modifier) {
        Text(text = top, color = color, style = smallLcd, maxLines = 1, softWrap = false)
        Text(text = bottom, color = color, style = smallLcd, maxLines = 1, softWrap = false)
    }
}

/**
 * Distance from `now` to the next occurrence of [alarmTime] (today if it's
 * still ahead, otherwise tomorrow). Returns null when no alarm is set.
 */
private fun computeCountdown(alarmTime: LocalTime?): HoursMinutes? {
    if (alarmTime == null) return null
    val tz = TimeZone.currentSystemDefault()
    val nowLocal = Clock.System.now().toLocalDateTime(tz)
    val targetDate = if (alarmTime > nowLocal.time) nowLocal.date else nowLocal.date.plus(1, DateTimeUnit.DAY)
    val targetInstant = LocalDateTime(targetDate, alarmTime).toInstant(tz)
    val remaining = targetInstant - Clock.System.now()
    val totalMinutes = remaining.inWholeMinutes.coerceAtLeast(0)
    return HoursMinutes(hours = totalMinutes / 60, minutes = totalMinutes % 60)
}

/** Two small filled dots stacked vertically — replaces the chunky Major Mono colon. */
@Composable
private fun ColonSeparator(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(width = 8.dp, height = 56.dp)) {
        drawColonDot(color, yFraction = 0.35f)
        drawColonDot(color, yFraction = 0.65f)
    }
}

private fun DrawScope.drawColonDot(color: Color, yFraction: Float) {
    val radius = size.width * 0.45f
    drawCircle(
        color = color,
        radius = radius,
        center = Offset(x = size.width / 2f, y = size.height * yFraction),
    )
}

/**
 * Shifts [text] left by the first glyph's side bearing so the painted ink
 * starts at x=0. Lets the Space Grotesk date label and the Major Mono Display
 * digits below it share the same visible left edge without per-font magic.
 */
@Composable
private fun AlignedFirstGlyph(
    text: String,
    color: Color,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    val resolver = LocalFontFamilyResolver.current
    val density = LocalDensity.current
    val leftBearingPx = remember(text, style, density.density) {
        if (text.isEmpty() || style.fontFamily == null) 0
        else runCatching {
            val typeface = resolver.resolve(
                fontFamily = style.fontFamily,
                fontWeight = style.fontWeight ?: FontWeight.Normal,
                fontStyle = style.fontStyle ?: FontStyle.Normal,
                fontSynthesis = FontSynthesis.None,
            ).value as? Typeface ?: return@runCatching 0
            val paint = Paint().apply {
                this.typeface = typeface
                this.textSize = with(density) { style.fontSize.toPx() }
                this.isAntiAlias = true
            }
            val rect = Rect()
            paint.getTextBounds(text, 0, 1, rect)
            rect.left
        }.getOrDefault(0)
    }
    Text(
        text = text,
        color = color,
        style = style,
        maxLines = 1,
        softWrap = false,
        modifier = modifier.layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            val shift = leftBearingPx
            val newWidth = (placeable.width - shift).coerceAtLeast(0)
            layout(newWidth, placeable.height) {
                placeable.place(-shift, 0)
            }
        },
    )
}

/**
 * Solid brand-orange tile with white tick marks across the strip and
 * near-black bars for each sleep-stage segment. Two timestamp labels at
 * the top, two stage labels at the bottom.
 */
@Composable
private fun SleepTimeline(
    segments: List<SleepSegment>,
    modifier: Modifier = Modifier,
) {
    val tile = MaterialTheme.colorScheme.primary
    val onTile = MaterialTheme.colorScheme.onPrimary
    val barColor = onTile.copy(alpha = 0.95f)
    Surface(
        color = tile,
        shape = RoundedCornerShape(Radius.lg),
        modifier = modifier
            .fillMaxWidth()
            .height(82.dp),
    ) {
        val tStart = segments.first().start
        val tEnd = segments.last().end
        val totalSpanMs = (tEnd - tStart).inWholeMilliseconds.coerceAtLeast(1)
        val tz = TimeZone.currentSystemDefault()

        // Pick two anchor segments for the labels — longest two by duration,
        // sorted chronologically so the left label is earlier than the right.
        val labelled = segments
            .sortedByDescending { (it.end - it.start).inWholeMilliseconds }
            .take(2)
            .sortedBy { it.start }
            .ifEmpty { listOf(segments.first()) }

        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = if (labelled.size == 1) Arrangement.Start else Arrangement.SpaceBetween,
            ) {
                labelled.forEach { seg ->
                    val local = seg.start.toLocalDateTime(tz)
                    Text(
                        text = String.format(Locale.US, "%02d:%02d", local.hour, local.minute),
                        fontFamily = MonoFontFamily,
                        fontSize = 16.sp,
                        color = onTile,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(20.dp),
            ) {
                val w = size.width
                val h = size.height
                val midY = h * 0.5f
                val tickCount = 80
                val tickColor = onTile.copy(alpha = 0.65f)
                for (i in 0 until tickCount) {
                    val x = w * i / (tickCount - 1).toFloat()
                    drawLine(
                        color = tickColor,
                        start = Offset(x, midY - 4f),
                        end = Offset(x, midY + 4f),
                        strokeWidth = 1.2f,
                    )
                }
                segments.forEach { seg ->
                    val frac0 = ((seg.start - tStart).inWholeMilliseconds.toFloat() / totalSpanMs).coerceIn(0f, 1f)
                    val frac1 = ((seg.end - tStart).inWholeMilliseconds.toFloat() / totalSpanMs).coerceIn(0f, 1f)
                    val x0 = w * frac0
                    val x1 = w * frac1
                    drawLine(
                        color = barColor,
                        start = Offset(x0, midY),
                        end = Offset(x1, midY),
                        strokeWidth = 3.5f,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = if (labelled.size == 1) Arrangement.Start else Arrangement.SpaceBetween,
            ) {
                labelled.forEach { seg ->
                    Text(
                        text = seg.stage.name.uppercase(Locale.ROOT),
                        fontFamily = MonoFontFamily,
                        fontSize = 14.sp,
                        color = onTile,
                    )
                }
            }
        }
    }
}
