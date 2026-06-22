package fr.bsodium.cron.ui.screens.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.bsodium.cron.ui.theme.CronColors
import fr.bsodium.cron.ui.theme.CronTheme
import fr.bsodium.cron.ui.theme.CronTypography
import fr.bsodium.cron.ui.theme.Radius
import fr.bsodium.cron.ui.theme.Spacing

internal val SESSION_GUTTER_WIDTH = 40.dp
private val TRACK_WIDTH = 1.5.dp
private val DISC_SIZE = 24.dp
private val EVENT_ICON_SIZE = 18.dp
private val STATION_DOT_SIZE = 6.dp


@Composable
internal fun SessionTimelineRow(
    firstLineHeight: Dp,
    isFirst: Boolean,
    isLast: Boolean,
    icon: (@Composable () -> Unit)? = null,
    discSize: Dp = DISC_SIZE,
    verticalPadding: Dp = Spacing.md,
    content: @Composable () -> Unit,
) {
    val ruleColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val maskColor = CronColors.pageBackground
    val discTop = (verticalPadding + (firstLineHeight - discSize) / 2 - 1.dp).coerceAtLeast(0.dp)
    val iconCenter = discTop + discSize / 2

    Box(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(SESSION_GUTTER_WIDTH))
            Spacer(Modifier.width(Spacing.sm))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(top = verticalPadding, bottom = verticalPadding, end = Spacing.md),
            ) { content() }
        }
        Box(modifier = Modifier.matchParentSize()) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .width(SESSION_GUTTER_WIDTH)
                    .fillMaxHeight()
                    .drawBehind {
                        if (isFirst && isLast) return@drawBehind
                        val cx = size.width / 2f
                        val top = if (isFirst) iconCenter.toPx() else 0f
                        val bottom = if (isLast) iconCenter.toPx() else size.height
                        drawLine(
                            color = ruleColor,
                            start = Offset(cx, top),
                            end = Offset(cx, bottom),
                            strokeWidth = TRACK_WIDTH.toPx(),
                            cap = StrokeCap.Round,
                        )
                    },
            ) {
                if (icon != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = discTop)
                            .size(discSize)
                            .clip(CircleShape)
                            .background(maskColor),
                        contentAlignment = Alignment.Center,
                    ) { icon() }
                }
            }
        }
    }
}

@Composable
internal fun SessionTimelineEventRow(
    firstLineHeight: Dp,
    isFirst: Boolean,
    isLast: Boolean,
    icon: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    SessionTimelineRow(
        firstLineHeight = firstLineHeight,
        isFirst = isFirst,
        isLast = isLast,
        icon = icon,
        discSize = EVENT_ICON_SIZE,
        verticalPadding = Spacing.sm,
        content = content,
    )
}

private val WAVE_AMPLITUDE = 3.dp
private val WAVE_LENGTH = 14.dp
private val WAVE_STROKE = 1.5.dp

@Composable
internal fun SessionTimelineDayHeader(
    label: String,
    @Suppress("UNUSED_PARAMETER") isFirst: Boolean,
    @Suppress("UNUSED_PARAMETER") isLast: Boolean,
) {
    val waveColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val bgColor = CronColors.pageBackground
    val density = LocalDensity.current
    val ampPx = with(density) { WAVE_AMPLITUDE.toPx() }
    val waveLenPx = with(density) { WAVE_LENGTH.toPx() }
    val strokePx = with(density) { WAVE_STROKE.toPx() }

    Box(
        modifier = Modifier
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
