package fr.bsodium.cron.ui.screens.home.components

import fr.bsodium.cron.ui.theme.CronTypography
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.bsodium.cron.session.model.SleepSegment
import fr.bsodium.cron.ui.theme.Radius
import fr.bsodium.cron.ui.theme.Spacing
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.util.Locale

// Canvas strokes in dp (resolved via toPx in the DrawScope) so bar/tick weights track density —
// the old raw-px literals rendered ~3x heavier on 1x screens. Values match the previous look at ~3.5x.
private val TICK_HALF_HEIGHT = 1.dp
private val TICK_STROKE = 0.4.dp
private val SEGMENT_STROKE = 1.dp
private val TIMELINE_HEIGHT = 82.dp
private val TICK_BAR_HEIGHT = 20.dp

/**
 * Inverse tile within the bold card: an `onPrimary` panel with `primary` tick marks and
 * segment bars. Two timestamp labels at the top, two stage labels at the bottom.
 */
@Composable
internal fun SleepTimeline(
    segments: List<SleepSegment>,
    modifier: Modifier = Modifier,
) {
    val tile = MaterialTheme.colorScheme.onPrimary
    val onTile = MaterialTheme.colorScheme.primary
    val barColor = onTile.copy(alpha = 0.95f)
    // Martian Mono's line box runs taller than the condensed face it replaced; trim the font
    // padding and pin line height to the point size so both label rows clear the fixed-height tile.
    val timeStyle = CronTypography.timeMono.copy(color = onTile)
    val stageStyle = timeStyle.copy(fontSize = 14.sp, lineHeight = 14.sp)
    Surface(
        color = tile,
        shape = RoundedCornerShape(Radius.lg),
        modifier = modifier
            .fillMaxWidth()
            .height(TIMELINE_HEIGHT),
    ) {
        val tStart = segments.first().start
        val tEnd = segments.last().end
        val totalSpanMs = (tEnd - tStart).inWholeMilliseconds.coerceAtLeast(1)
        val tz = TimeZone.currentSystemDefault()

        // Two anchor segments for labels: the longest two by duration, sorted chronologically (left earlier).
        val labelled = segments
            .sortedByDescending { (it.end - it.start).inWholeMilliseconds }
            .take(2)
            .sortedBy { it.start }
            .ifEmpty { listOf(segments.first()) }

        Column(modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm + Spacing.xxs)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = if (labelled.size == 1) Arrangement.Start else Arrangement.SpaceBetween,
            ) {
                labelled.forEach { seg ->
                    val local = seg.start.toLocalDateTime(tz)
                    Text(
                        text = String.format(Locale.US, "%02d:%02d", local.hour, local.minute),
                        style = timeStyle,
                    )
                }
            }
            Spacer(Modifier.height(Spacing.xs))
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(TICK_BAR_HEIGHT),
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
                        start = Offset(x, midY - TICK_HALF_HEIGHT.toPx()),
                        end = Offset(x, midY + TICK_HALF_HEIGHT.toPx()),
                        strokeWidth = TICK_STROKE.toPx(),
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
                        strokeWidth = SEGMENT_STROKE.toPx(),
                    )
                }
            }
            Spacer(Modifier.height(Spacing.xs))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = if (labelled.size == 1) Arrangement.Start else Arrangement.SpaceBetween,
            ) {
                labelled.forEach { seg ->
                    Text(
                        text = seg.stage.name.uppercase(Locale.ROOT),
                        style = stageStyle,
                    )
                }
            }
        }
    }
}
