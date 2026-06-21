@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package fr.bsodium.cron.ui.screens.home.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.bsodium.cron.ui.screens.home.AiIterationUi
import fr.bsodium.cron.ui.screens.home.AiThreadUi
import fr.bsodium.cron.ui.screens.home.RunKind
import fr.bsodium.cron.ui.theme.CronTheme
import fr.bsodium.cron.ui.theme.MaterialSymbol
import fr.bsodium.cron.ui.theme.Radius
import fr.bsodium.cron.ui.theme.Spacing
import fr.bsodium.cron.ui.theme.Symbol

private val CARD_SHAPE = RoundedCornerShape(Radius.lg)
private val GUTTER_ICON_SIZE = 22.dp
private val GUTTER_LOADER_SIZE = 24.dp

@Composable
internal fun PlanTimelineCard(
    iteration: AiIterationUi,
    isLatest: Boolean,
    isStreaming: Boolean,
    isFirst: Boolean,
    isLast: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isNew: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val containerColor = if (isLatest) scheme.primaryContainer else scheme.secondaryContainer
    val contentColor = if (isLatest) scheme.onPrimaryContainer else scheme.onSecondaryContainer
    val enter = remember { Animatable(if (isNew) 0f else 1f) }
    val enterSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    if (isNew) LaunchedEffect(Unit) { enter.animateTo(1f, enterSpec) }

    SessionTimelineRow(
        firstLineHeight = 48.dp,
        isFirst = isFirst,
        isLast = isLast,
        icon = {
            if (isStreaming) {
                ContainedLoadingIndicator(
                    modifier = Modifier.size(GUTTER_LOADER_SIZE),
                    containerShape = CircleShape,
                    containerColor = scheme.primaryContainer,
                    indicatorColor = scheme.onPrimaryContainer,
                )
            } else {
                val icon = if (iteration.thread.isMocked) MaterialSymbol.Code else runSymbol(iteration.kind)
                Symbol(symbol = icon, contentDescription = null, tint = contentColor, size = GUTTER_ICON_SIZE)
            }
        },
    ) {
        Surface(
            onClick = onClick,
            modifier = modifier
                .graphicsLayer {
                    alpha = enter.value
                    val s = 0.85f + 0.15f * enter.value
                    scaleX = s; scaleY = s
                }
                .fillMaxWidth()
                .heightIn(min = 48.dp),
            shape = CARD_SHAPE,
            color = containerColor,
            contentColor = contentColor,
        ) {
            Row(
                modifier = Modifier.padding(Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = iteration.systemMessage,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val meta = if (isLatest) "Latest · ${iteration.timeLabel}" else iteration.timeLabel
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.labelSmall,
                        color = contentColor.copy(alpha = 0.75f),
                        maxLines = 1,
                    )
                    val preview = iteration.thread.response?.take(80)?.replace('\n', ' ')
                    if (!preview.isNullOrBlank()) {
                        Text(
                            text = preview,
                            style = MaterialTheme.typography.bodyMedium,
                            color = contentColor.copy(alpha = 0.65f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Symbol(
                    symbol = MaterialSymbol.ArrowForward,
                    contentDescription = null,
                    tint = contentColor.copy(alpha = 0.6f),
                    size = 20.dp,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PlanTimelineCardPreview() {
    val iteration = AiIterationUi(
        turnIndex = 0,
        timeLabel = "23:14",
        kind = RunKind.ScheduledBase,
        thread = AiThreadUi(
            turnIndex = 0,
            summary = "Thought for 8s",
            process = emptyList(),
            response = "Set alarm for 7:15. Your first meeting is at 9:00.",
        ),
        ranAtEpochMs = System.currentTimeMillis(),
    )
    CronTheme {
        Column(modifier = Modifier.padding(Spacing.lg)) {
            PlanTimelineCard(
                iteration = iteration,
                isLatest = true,
                isStreaming = false,
                isFirst = true,
                isLast = false,
                onClick = {},
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PlanTimelineCardStreamingPreview() {
    val iteration = AiIterationUi(
        turnIndex = 1,
        timeLabel = "23:20",
        kind = RunKind.Replan(null),
        thread = AiThreadUi(
            turnIndex = 1,
            summary = "Thinking...",
            process = emptyList(),
            response = null,
            isStreaming = true,
        ),
        ranAtEpochMs = System.currentTimeMillis(),
    )
    CronTheme {
        Column(modifier = Modifier.padding(Spacing.lg)) {
            PlanTimelineCard(
                iteration = iteration,
                isLatest = true,
                isStreaming = true,
                isFirst = false,
                isLast = true,
                onClick = {},
                isNew = true,
            )
        }
    }
}
