package fr.bsodium.cron.ui.screens.home.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.bsodium.cron.session.model.SleepSegment
import fr.bsodium.cron.ui.components.PillBadge
import fr.bsodium.cron.ui.theme.BrandOnOrange
import fr.bsodium.cron.ui.theme.BrandOrange
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
    isRetrying: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .border(width = 2.dp, color = BrandOrange, shape = RoundedCornerShape(28.dp)),
        color = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(28.dp),
        tonalElevation = 0.dp,
    ) {
        Column(modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 22.dp, bottom = 22.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = dateLabel.ifBlank { "—" },
                    fontFamily = MonoFontFamily,
                    fontSize = 22.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .weight(1f)
                        .padding(top = 6.dp),
                )
                RetryButton(spinning = isRetrying, onClick = onRetry)
            }
            Spacer(Modifier.height(2.dp))
            LcdTimeDisplay(alarmTime = alarmTime)
            Spacer(Modifier.height(18.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
            )
            Spacer(Modifier.height(18.dp))
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
                        containerColor = BrandOrange,
                        contentColor = BrandOnOrange,
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

@Composable
private fun LcdTimeDisplay(alarmTime: LocalTime?, modifier: Modifier = Modifier) {
    // Live seconds tick — only re-emits every second.
    val seconds by produceState(initialValue = currentSeconds()) {
        while (true) {
            value = currentSeconds()
            delay(1_000)
        }
    }
    val display = alarmTime
    val hhmm = display?.let { String.format("%02d:%02d", it.hour, it.minute) } ?: "--:--"
    val ss = display?.let { String.format("%02d", seconds) } ?: "--"

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = hhmm,
            fontSize = 76.sp,
            lineHeight = 76.sp,
            fontFamily = LcdFontFamily,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            softWrap = false,
        )
        Text(
            text = ss,
            fontSize = 24.sp,
            lineHeight = 24.sp,
            fontFamily = LcdFontFamily,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 6.dp, top = 6.dp),
            maxLines = 1,
            softWrap = false,
        )
    }
}

private fun currentSeconds(): Int =
    Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).second

@Composable
private fun RetryButton(spinning: Boolean, onClick: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "retry-spin")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = -360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "retry-spin-angle",
    )
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(48.dp)
            .background(BrandOrange, CircleShape),
        colors = IconButtonDefaults.iconButtonColors(contentColor = BrandOnOrange),
    ) {
        Icon(
            imageVector = Icons.Filled.Refresh,
            contentDescription = "Re-run alarm prediction",
            modifier = if (spinning) Modifier.rotate(angle) else Modifier,
        )
    }
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
    Surface(
        color = BrandOrange,
        shape = RoundedCornerShape(18.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(82.dp),
    ) {
        if (segments.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "No sleep data yet",
                    fontFamily = MonoFontFamily,
                    fontSize = 16.sp,
                    color = BrandOnOrange.copy(alpha = 0.9f),
                    textAlign = TextAlign.Center,
                )
            }
            return@Surface
        }

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
                        color = BrandOnOrange,
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
                val tickColor = BrandOnOrange.copy(alpha = 0.65f)
                for (i in 0 until tickCount) {
                    val x = w * i / (tickCount - 1).toFloat()
                    drawLine(
                        color = tickColor,
                        start = Offset(x, midY - 4f),
                        end = Offset(x, midY + 4f),
                        strokeWidth = 1.2f,
                    )
                }
                // Stage overlay bars — near-black horizontal lines across each segment.
                val barColor = Color(0xFF1A0A04)
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
                        color = BrandOnOrange,
                    )
                }
            }
        }
    }
}
