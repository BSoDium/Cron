package fr.bsodium.cron.ui.screens.home.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.bsodium.cron.session.model.SleepSegment
import fr.bsodium.cron.ui.components.PillBadge
import fr.bsodium.cron.ui.theme.LcdFontFamily
import fr.bsodium.cron.ui.theme.MonoFontFamily
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

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
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(28.dp),
        tonalElevation = 4.dp,
        shadowElevation = 2.dp,
    ) {
        Column(modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 22.dp, bottom = 22.dp)) {
            Text(
                text = dateLabel.ifBlank { "—" },
                color = MaterialTheme.colorScheme.onSurface,
                style = TightTextStyle.copy(
                    fontFamily = MonoFontFamily,
                    fontSize = 22.sp,
                    lineHeight = 22.sp,
                ),
            )
            LcdTimeDisplay(alarmTime = alarmTime)
            if (sleepSegments.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
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
                Spacer(Modifier.height(14.dp))
                SleepTimeline(segments = sleepSegments)
            }
        }
    }
}

@Composable
private fun LcdTimeDisplay(alarmTime: LocalTime?, modifier: Modifier = Modifier) {
    // Live seconds tick — only re-emits every second.
    val seconds by produceState(initialValue = currentSeconds()) {
        while (true) {
            value = currentSeconds()
            delay(1_000)
        }
    }
    val pending = alarmTime == null
    val hh = alarmTime?.let { String.format("%02d", it.hour) } ?: "00"
    val mm = alarmTime?.let { String.format("%02d", it.minute) } ?: "00"
    val ss = if (pending) "00" else String.format("%02d", seconds)
    val base = MaterialTheme.colorScheme.onSurface
    val digitColor = if (pending) base.copy(alpha = 0.22f) else base
    val ssColor = if (pending) base.copy(alpha = 0.16f) else base.copy(alpha = 0.6f)

    val lcdStyle = TightTextStyle.copy(
        fontFamily = LcdFontFamily,
        fontSize = 76.sp,
        lineHeight = 76.sp,
    )

    Row(
        // Compensate for Major Mono Display's left side bearing so the visible
        // left edge of the digits lines up with the date label's first letter.
        modifier = modifier.offset(x = (-6).dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(text = hh, color = digitColor, style = lcdStyle, maxLines = 1, softWrap = false)
        ColonSeparator(
            color = digitColor,
            modifier = Modifier
                .align(Alignment.CenterVertically)
                .padding(horizontal = 8.dp),
        )
        Text(text = mm, color = digitColor, style = lcdStyle, maxLines = 1, softWrap = false)
        Text(
            text = ss,
            color = ssColor,
            style = TightTextStyle.copy(
                fontFamily = LcdFontFamily,
                fontSize = 24.sp,
                lineHeight = 24.sp,
            ),
            modifier = Modifier.padding(start = 6.dp, top = 6.dp),
            maxLines = 1,
            softWrap = false,
        )
    }
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

private fun currentSeconds(): Int =
    Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).second

/**
 * Shared text style that strips font padding and trims the line-height box so
 * adjacent rows of LCD/mono text sit flush against each other (Compose's
 * default `includeFontPadding=true` adds extra leading above tall glyphs).
 */
private val TightTextStyle: TextStyle = TextStyle(
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.Both,
    ),
)

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
        shape = RoundedCornerShape(18.dp),
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
                        text = String.format("%02d:%02d", local.hour, local.minute),
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
                        text = seg.stage.name.uppercase(),
                        fontFamily = MonoFontFamily,
                        fontSize = 14.sp,
                        color = onTile,
                    )
                }
            }
        }
    }
}
