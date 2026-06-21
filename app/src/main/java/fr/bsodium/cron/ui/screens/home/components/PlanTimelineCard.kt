@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package fr.bsodium.cron.ui.screens.home.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.bsodium.cron.session.model.TriggerType
import fr.bsodium.cron.ui.screens.home.AiIterationUi
import fr.bsodium.cron.ui.screens.home.AiThreadUi
import fr.bsodium.cron.ui.screens.home.RunKind
import fr.bsodium.cron.ui.theme.CronTheme
import fr.bsodium.cron.ui.theme.MaterialSymbol
import fr.bsodium.cron.ui.theme.Spacing
import fr.bsodium.cron.ui.theme.Symbol

private val CARD_SHAPE = RoundedCornerShape(50)
private val ICON_BOX = 30.dp
private val ICON_GLYPH = 22.dp
private val CHEVRON_ICON_SIZE = 18.dp
private val TRACK_WIDTH = 1.5.dp

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
    val containerColor = if (isLatest) scheme.primary else scheme.secondaryContainer
    val contentColor = if (isLatest) scheme.onPrimary else scheme.onSecondaryContainer
    val ruleColor = scheme.surfaceContainerHighest
    val enter = remember { Animatable(if (isNew) 0f else 1f) }
    val enterSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    if (isNew) LaunchedEffect(Unit) { enter.animateTo(1f, enterSpec) }

    val trackCx = with(LocalDensity.current) { (SESSION_GUTTER_WIDTH / 2).toPx() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                if (isFirst && isLast) return@drawBehind
                val top = if (isFirst) size.height / 2f else 0f
                val bottom = if (isLast) size.height / 2f else size.height
                drawLine(
                    color = ruleColor,
                    start = Offset(trackCx, top),
                    end = Offset(trackCx, bottom),
                    strokeWidth = TRACK_WIDTH.toPx(),
                    cap = StrokeCap.Round,
                )
            },
    ) {
        Surface(
            onClick = onClick,
            modifier = Modifier
                .graphicsLayer {
                    alpha = enter.value
                    val s = 0.85f + 0.15f * enter.value
                    scaleX = s; scaleY = s
                }
                .fillMaxWidth()
                .padding(vertical = Spacing.sm)
                .heightIn(min = 48.dp),
            shape = CARD_SHAPE,
            color = containerColor,
            contentColor = contentColor,
        ) {
            Row(
                modifier = Modifier.padding(
                    start = Spacing.sm,
                    top = Spacing.sm,
                    end = Spacing.xl,
                    bottom = Spacing.sm,
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.size(ICON_BOX), contentAlignment = Alignment.Center) {
                    if (isStreaming) {
                        ContainedLoadingIndicator(
                            modifier = Modifier.fillMaxSize(),
                            containerShape = CircleShape,
                            containerColor = contentColor.copy(alpha = 0.2f),
                            indicatorColor = contentColor,
                        )
                    } else {
                        val icon = if (iteration.thread.isMocked) MaterialSymbol.Code else runSymbol(iteration.kind)
                        Symbol(symbol = icon, contentDescription = null, tint = contentColor, size = ICON_GLYPH)
                    }
                }
                Spacer(Modifier.width(Spacing.sm))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = iteration.systemMessage,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
                    )
                    val meta = if (isLatest) "Latest · ${iteration.timeLabel}" else iteration.timeLabel
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.labelSmall,
                        color = contentColor.copy(alpha = 0.75f),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
                    )
                }
                Symbol(
                    symbol = MaterialSymbol.ArrowForward,
                    contentDescription = null,
                    tint = contentColor.copy(alpha = 0.5f),
                    size = CHEVRON_ICON_SIZE,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PlanTimelineCardPreview() {
    val iteration = AiIterationUi(
        turnIndex = 0, timeLabel = "23:14", kind = RunKind.ScheduledBase,
        thread = AiThreadUi(0, "Thought for 8s", emptyList(), "Set alarm for 7:15."),
        ranAtEpochMs = System.currentTimeMillis(),
    )
    CronTheme {
        Column(modifier = Modifier.padding(horizontal = Spacing.xl)) {
            PlanTimelineCard(
                iteration = iteration, isLatest = true, isStreaming = false,
                isFirst = true, isLast = false, onClick = {},
            )
            PlanTimelineCard(
                iteration = iteration.copy(
                    turnIndex = 1, kind = RunKind.Replan(TriggerType.CalendarChange), timeLabel = "21:30",
                ),
                isLatest = false, isStreaming = false,
                isFirst = false, isLast = false, onClick = {},
            )
            PlanTimelineCard(
                iteration = iteration.copy(
                    turnIndex = 2, kind = RunKind.Replan(TriggerType.AlarmSnoozed), timeLabel = "07:15",
                    thread = AiThreadUi(2, "Thinking...", emptyList(), null, isStreaming = true),
                ),
                isLatest = true, isStreaming = true,
                isFirst = false, isLast = true, onClick = {},
            )
        }
    }
}
