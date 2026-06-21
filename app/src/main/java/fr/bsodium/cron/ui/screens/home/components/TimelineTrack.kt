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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
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

internal val SESSION_GUTTER_WIDTH = 28.dp
private val TRACK_WIDTH = 2.dp
private val DISC_SIZE = 24.dp
private val EVENT_ICON_SIZE = 18.dp
private val STATION_DOT_SIZE = 6.dp

private val DASH_ON = 2f
private val DASH_OFF = 5f

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
    val discTop = (verticalPadding + (firstLineHeight - discSize) / 2).coerceAtLeast(0.dp)
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
                            pathEffect = PathEffect.dashPathEffect(
                                floatArrayOf(DASH_ON.dp.toPx(), DASH_OFF.dp.toPx()),
                            ),
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

@Composable
internal fun SessionTimelineDayHeader(
    label: String,
    isFirst: Boolean,
    isLast: Boolean,
) {
    val dotColor = MaterialTheme.colorScheme.surfaceContainerHighest
    SessionTimelineRow(
        firstLineHeight = 20.dp,
        isFirst = isFirst,
        isLast = isLast,
        icon = {
            Box(
                modifier = Modifier
                    .size(STATION_DOT_SIZE)
                    .clip(CircleShape)
                    .background(dotColor),
            )
        },
        discSize = STATION_DOT_SIZE,
        verticalPadding = Spacing.md,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
