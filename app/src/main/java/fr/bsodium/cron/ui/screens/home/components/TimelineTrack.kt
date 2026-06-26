package fr.bsodium.cron.ui.screens.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.bsodium.cron.ui.theme.CronColors
import fr.bsodium.cron.ui.theme.CronTheme
import fr.bsodium.cron.ui.theme.CronTypography
import fr.bsodium.cron.ui.theme.Radius
import fr.bsodium.cron.ui.theme.Spacing

private val WAVE_AMPLITUDE = 3.dp
private val WAVE_LENGTH = 14.dp
private val WAVE_STROKE = 1.5.dp

@Composable
internal fun SessionTimelineDayHeader(
    label: String,
    @Suppress("UNUSED_PARAMETER") isFirst: Boolean,
    @Suppress("UNUSED_PARAMETER") isLast: Boolean,
    modifier: Modifier = Modifier,
) {
    val waveColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val bgColor = CronColors.pageBackground
    val density = LocalDensity.current
    val ampPx = with(density) { WAVE_AMPLITUDE.toPx() }
    val waveLenPx = with(density) { WAVE_LENGTH.toPx() }
    val strokePx = with(density) { WAVE_STROKE.toPx() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.lg)
            .drawBehind {
                val cy = size.height / 2f
                val path = androidx.compose.ui.graphics.Path()
                path.moveTo(0f, cy)
                var x = 0f
                while (x < size.width) {
                    path.quadraticTo(x + waveLenPx / 4f, cy - ampPx, x + waveLenPx / 2f, cy)
                    path.quadraticTo(x + waveLenPx * 3f / 4f, cy + ampPx, x + waveLenPx, cy)
                    x += waveLenPx
                }
                drawPath(
                    path = path,
                    color = waveColor,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = strokePx,
                        cap = StrokeCap.Round,
                    ),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .background(bgColor)
                .padding(horizontal = Spacing.md),
        )
    }
}

@Composable
internal fun MonoPill(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(Radius.sm),
    ) {
        Text(
            text = text,
            style = CronTypography.labelMonoSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xxs),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun TimelineTrackPreview() {
    CronTheme {
        Box(modifier = Modifier.padding(Spacing.lg)) {
            SessionTimelineDayHeader(label = "Today", isFirst = true, isLast = false)
        }
    }
}
