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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.bsodium.cron.session.model.SleepSegment
import fr.bsodium.cron.session.model.SleepStage
import fr.bsodium.cron.ui.components.PillBadge
import fr.bsodium.cron.ui.components.SectionLabel
import fr.bsodium.cron.ui.theme.LcdFontFamily
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * The hero card on the home screen.
 *
 *   Tuesday 17              [↻]
 *   08:26⁴³
 *   ─────────────
 *   Sleep                 8H 32M
 *   ▂▂▂▂▃▃▅▅▅▅▃▃▂▂▂  (timeline)
 *   10:32         03:32
 *    REM           REM
 */
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
    val accent = MaterialTheme.colorScheme.primary
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .border(width = 2.dp, color = accent, shape = RoundedCornerShape(28.dp)),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(28.dp),
        tonalElevation = 0.dp,
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = dateLabel.ifBlank { "—" },
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = LcdFontFamily,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .weight(1f)
                        .padding(top = 8.dp),
                )
                RetryButton(spinning = isRetrying, onClick = onRetry, tint = accent)
            }
            Spacer(Modifier.height(4.dp))
            LcdTimeDisplay(alarmTime = alarmTime)
            Spacer(Modifier.height(20.dp))
            Surface(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp),
            ) {}
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionLabel(text = "Sleep", modifier = Modifier.weight(1f))
                if (sleepDurationLabel != null) {
                    PillBadge(
                        text = sleepDurationLabel,
                        containerColor = accent,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            SleepTimeline(segments = sleepSegments, accent = accent)
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
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = hhmm,
            fontSize = 80.sp,
            fontFamily = LcdFontFamily,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.size(6.dp))
        Text(
            text = ss,
            fontSize = 24.sp,
            fontFamily = LcdFontFamily,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

private fun currentSeconds(): Int =
    Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).second

@Composable
private fun RetryButton(spinning: Boolean, onClick: () -> Unit, tint: Color) {
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
            .background(tint, CircleShape),
        colors = IconButtonDefaults.iconButtonColors(
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        Icon(
            imageVector = Icons.Filled.Refresh,
            contentDescription = "Re-run alarm prediction",
            modifier = if (spinning) Modifier.rotate(angle) else Modifier,
        )
    }
}

/**
 * Horizontal sleep-stage timeline. Dense tick marks across the strip,
 * with REM/Deep/Light stages overlaid as colored bars.
 */
@Composable
private fun SleepTimeline(
    segments: List<SleepSegment>,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val barColor = MaterialTheme.colorScheme.onPrimary
    Surface(
        color = accent,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp),
    ) {
        if (segments.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "No sleep data yet",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                )
            }
            return@Surface
        }

        val tStart = segments.first().start
        val tEnd = segments.last().end
        val totalSpanMs = (tEnd - tStart).inWholeMilliseconds.coerceAtLeast(1)
        val tz = TimeZone.currentSystemDefault()

        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            // Two timestamp labels: longest two REM windows (or first/last segment as fallback).
            val labelled = segments
                .filter { it.stage == SleepStage.Rem }
                .sortedByDescending { it.duration }
                .take(2)
                .sortedBy { it.start }
                .ifEmpty { listOf(segments.first(), segments.last()).distinct() }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                labelled.forEach { seg ->
                    val local = seg.start.toLocalDateTime(tz)
                    Text(
                        text = String.format("%02d:%02d", local.hour, local.minute),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontFamily = LcdFontFamily,
                    )
                }
            }
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(22.dp),
            ) {
                val w = size.width
                val h = size.height
                val tickCount = 60
                val tickColor = barColor.copy(alpha = 0.45f)
                // Background ticks.
                for (i in 0 until tickCount) {
                    val x = w * i / tickCount.toFloat()
                    drawLine(
                        color = tickColor,
                        start = Offset(x, h * 0.5f - 3f),
                        end = Offset(x, h * 0.5f + 3f),
                        strokeWidth = 1.2f,
                    )
                }
                // Stage overlays.
                segments.forEach { seg ->
                    val frac0 = ((seg.start - tStart).inWholeMilliseconds.toFloat() / totalSpanMs).coerceIn(0f, 1f)
                    val frac1 = ((seg.end - tStart).inWholeMilliseconds.toFloat() / totalSpanMs).coerceIn(0f, 1f)
                    val x0 = w * frac0
                    val x1 = w * frac1
                    val band = when (seg.stage) {
                        SleepStage.Awake -> 1.0f
                        SleepStage.Rem -> 0.85f
                        SleepStage.Light -> 0.6f
                        SleepStage.Deep -> 0.3f
                    }
                    drawLine(
                        color = barColor,
                        start = Offset(x0, h * 0.5f),
                        end = Offset(x1, h * 0.5f),
                        strokeWidth = (band * h * 0.55f).coerceAtLeast(3f),
                    )
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                labelled.forEach { seg ->
                    Text(
                        text = seg.stage.name.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontFamily = LcdFontFamily,
                    )
                }
            }
        }
    }
}

