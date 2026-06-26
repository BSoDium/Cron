@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package fr.bsodium.cron.ui.screens.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import fr.bsodium.cron.session.model.TriggerType
import fr.bsodium.cron.ui.screens.home.AiIterationUi
import fr.bsodium.cron.ui.screens.home.AiThreadUi
import fr.bsodium.cron.ui.screens.home.ProcessItem
import fr.bsodium.cron.ui.screens.home.RunKind
import fr.bsodium.cron.ui.screens.home.TimelineItem
import fr.bsodium.cron.ui.theme.CronTheme
import fr.bsodium.cron.ui.theme.CronTypography
import fr.bsodium.cron.ui.theme.MaterialSymbol
import fr.bsodium.cron.ui.theme.Spacing
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.util.Locale

internal fun LazyListScope.sessionTimelineItems(
    timeline: List<TimelineItem>,
    hasMore: Boolean,
    onOpenAiRun: (turnIndex: Int, sessionId: String) -> Unit,
    onNavigateToHistory: () -> Unit,
) {
    item(key = "timeline-top-spacer") {
        Spacer(Modifier.height(Spacing.xxxl))
    }

    items(
        count = timeline.size,
        key = { timeline[it].id },
        contentType = { timeline[it]::class },
    ) { index ->
        val item = timeline[index]
        val isFirst = index == 0 || timeline[index - 1] is TimelineItem.DayHeader
        val isLast = index == timeline.lastIndex || timeline.getOrNull(index + 1) is TimelineItem.DayHeader

        when (item) {
            is TimelineItem.DayHeader -> SessionTimelineDayHeader(
                label = item.label,
                isFirst = index == 0,
                isLast = index == timeline.lastIndex,
                modifier = Modifier.animateItem(fadeInSpec = null, placementSpec = null, fadeOutSpec = null),
            )
            is TimelineItem.AiRun -> AiRunNode(
                item = item,
                isFirst = isFirst,
                isLast = isLast,
                onClick = { onOpenAiRun(item.iteration.turnIndex, item.sessionId) },
                modifier = Modifier.animateItem(fadeInSpec = null, placementSpec = null, fadeOutSpec = null),
            )
            is TimelineItem.Event -> EventNode(
                item = item,
                isFirst = isFirst,
                isLast = isLast,
                modifier = Modifier.animateItem(fadeInSpec = null, placementSpec = null, fadeOutSpec = null),
            )
        }
    }

    if (hasMore) {
        item(key = "view-history") {
            OutlinedButton(
                onClick = onNavigateToHistory,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Spacing.lg),
                shape = RoundedCornerShape(50),
            ) {
                Text("View full history")
            }
        }
    }
}

@Composable
private fun AiRunNode(
    item: TimelineItem.AiRun,
    isFirst: Boolean,
    isLast: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val iter = item.iteration
    val anchor = when {
        item.isStreaming -> TimelineAnchor.Loader
        iter.thread.isMocked -> TimelineAnchor.Icon(
            symbol = MaterialSymbol.Code,
            tint = if (item.isLatest) scheme.primary else null,
            containerColor = if (item.isLatest) scheme.primaryContainer else null,
        )
        else -> TimelineAnchor.Icon(
            symbol = runSymbol(iter.kind),
            tint = if (item.isLatest) scheme.primary else null,
            containerColor = if (item.isLatest) scheme.primaryContainer else null,
        )
    }
    val contentColor = scheme.onSurfaceVariant

    TimelineNode(
        anchor = anchor,
        isFirst = isFirst,
        isLast = isLast,
        onClick = onClick,
        modifier = modifier,
        title = {
            Text(
                text = iter.systemMessage,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        },
        status = {
            val meta = if (item.isLatest) "Latest · ${iter.timeLabel}" else iter.timeLabel
            Text(
                text = meta,
                style = CronTypography.labelMonoSmall,
                color = contentColor,
                maxLines = 1,
                softWrap = false,
            )
        },
    )
}

@Composable
private fun EventNode(
    item: TimelineItem.Event,
    isFirst: Boolean,
    isLast: Boolean,
    modifier: Modifier = Modifier,
) {
    val contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    val tz = TimeZone.currentSystemDefault()
    val local = item.timestamp.toLocalDateTime(tz)
    val timeText = String.format(Locale.US, "%02d:%02d", local.hour, local.minute)

    TimelineNode(
        anchor = TimelineAnchor.Icon(
            symbol = triggerSymbol(item.trigger),
            tint = contentColor,
        ),
        isFirst = isFirst,
        isLast = isLast,
        verticalPadding = Spacing.md,
        modifier = modifier,
        title = {
            Text(
                text = item.label,
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        status = {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                if (item.detail != null) {
                    MonoPill(text = item.detail)
                }
                Text(
                    text = timeText,
                    style = CronTypography.labelMonoSmall,
                    color = contentColor,
                )
            }
        },
    )
}

private fun previewIteration(
    turn: Int,
    kind: RunKind,
    response: String?,
    isStreaming: Boolean = false,
    process: List<ProcessItem> = emptyList(),
) = AiIterationUi(
    turnIndex = turn,
    timeLabel = if (turn == 0) "23:14" else "21:30",
    kind = kind,
    thread = AiThreadUi(
        turnIndex = turn,
        summary = if (isStreaming) "Thinking..." else "Thought for 8s",
        process = process,
        response = response,
        isStreaming = isStreaming,
    ),
    ranAtEpochMs = System.currentTimeMillis(),
)

@Preview(showBackground = true, name = "Timeline — full example")
@Composable
private fun SessionTimelinePreview() {
    val now = Instant.fromEpochMilliseconds(System.currentTimeMillis())
    val yesterday = Instant.fromEpochMilliseconds(System.currentTimeMillis() - 86_400_000L)
    val twoDaysAgo = Instant.fromEpochMilliseconds(System.currentTimeMillis() - 172_800_000L)
    val timeline = listOf(
        TimelineItem.AiRun(
            timestamp = now,
            iteration = previewIteration(
                turn = 3,
                kind = RunKind.Replan(TriggerType.AlarmSnoozed),
                response = null,
                isStreaming = true,
            ),
            sessionId = "s1",
            isStreaming = true,
            isLatest = true,
        ),
        TimelineItem.Event(
            timestamp = now,
            trigger = TriggerType.AlarmSnoozed,
            label = "Alarm snoozed",
            detail = "9 min",
        ),
        TimelineItem.AiRun(
            timestamp = now,
            iteration = previewIteration(
                turn = 2,
                kind = RunKind.Replan(TriggerType.CalendarChange),
                response = "Moved alarm to **07:15** — your first meeting shifted to 09:00.",
                process = listOf(
                    ProcessItem.Reasoning("Checking calendar for changes..."),
                    ProcessItem.Tool("read_calendar", isComplete = true, contextLabel = "3 events"),
                ),
            ),
            sessionId = "s1",
            isStreaming = false,
            isLatest = false,
        ),
        TimelineItem.Event(
            timestamp = now,
            trigger = TriggerType.SleepOnset,
            label = "You fell asleep",
            detail = null,
        ),
        TimelineItem.AiRun(
            timestamp = now,
            iteration = previewIteration(
                turn = 0,
                kind = RunKind.ScheduledBase,
                response = "Set alarm for **07:45**. You have a 08:30 standup.",
                process = listOf(
                    ProcessItem.Reasoning("Looking at tomorrow's calendar..."),
                    ProcessItem.Tool("read_calendar", isComplete = true, contextLabel = "5 events"),
                    ProcessItem.Tool("set_alarm", isComplete = true, contextLabel = "07:45"),
                ),
            ),
            sessionId = "s1",
            isStreaming = false,
            isLatest = false,
        ),
        TimelineItem.DayHeader(timestamp = yesterday, label = "Yesterday"),
        TimelineItem.Event(
            timestamp = yesterday,
            trigger = TriggerType.OutOfBedConfirmed,
            label = "You got up",
            detail = null,
        ),
        TimelineItem.Event(
            timestamp = yesterday,
            trigger = TriggerType.AlarmDismissed,
            label = "Alarm dismissed",
            detail = null,
        ),
        TimelineItem.AiRun(
            timestamp = yesterday,
            iteration = previewIteration(
                turn = 1,
                kind = RunKind.Replan(TriggerType.SleepOnset),
                response = "Adjusted alarm to **07:30** — you fell asleep earlier than expected.",
                process = listOf(
                    ProcessItem.Reasoning("Re-evaluating wake window..."),
                    ProcessItem.Tool("set_alarm", isComplete = true, contextLabel = "07:30"),
                ),
            ),
            sessionId = "s2",
            isStreaming = false,
            isLatest = false,
        ),
        TimelineItem.Event(
            timestamp = yesterday,
            trigger = TriggerType.SleepOnset,
            label = "You fell asleep",
            detail = null,
        ),
        TimelineItem.AiRun(
            timestamp = yesterday,
            iteration = previewIteration(
                turn = 0,
                kind = RunKind.ScheduledBase,
                response = "Set alarm for **07:45**. First meeting at 09:30.",
                process = listOf(
                    ProcessItem.Tool("read_calendar", isComplete = true, contextLabel = "4 events"),
                    ProcessItem.Tool("estimate_commute", isComplete = true, contextLabel = "25 min"),
                    ProcessItem.Tool("set_alarm", isComplete = true, contextLabel = "07:45"),
                ),
            ),
            sessionId = "s2",
            isStreaming = false,
            isLatest = false,
        ),
        TimelineItem.DayHeader(timestamp = twoDaysAgo, label = "Thu 19 Jun"),
        TimelineItem.Event(
            timestamp = twoDaysAgo,
            trigger = TriggerType.HardLatestFired,
            label = "Safety alarm fired",
            detail = null,
        ),
        TimelineItem.AiRun(
            timestamp = twoDaysAgo,
            iteration = previewIteration(
                turn = 0,
                kind = RunKind.ManualBase,
                response = "No alarm needed — your calendar is free tomorrow.",
            ),
            sessionId = "s3",
            isStreaming = false,
            isLatest = false,
        ),
    )
    CronTheme {
        Column(modifier = Modifier.padding(horizontal = Spacing.xl)) {
            timeline.forEachIndexed { index, item ->
                val isFirst = index == 0 || timeline[index - 1] is TimelineItem.DayHeader
                val isLast = index == timeline.lastIndex || timeline.getOrNull(index + 1) is TimelineItem.DayHeader
                when (item) {
                    is TimelineItem.DayHeader -> SessionTimelineDayHeader(
                        label = item.label,
                        isFirst = index == 0,
                        isLast = index == timeline.lastIndex,
                    )
                    is TimelineItem.AiRun -> AiRunNode(
                        item = item,
                        isFirst = isFirst,
                        isLast = isLast,
                        onClick = {},
                    )
                    is TimelineItem.Event -> EventNode(
                        item = item,
                        isFirst = isFirst,
                        isLast = isLast,
                    )
                }
            }
        }
    }
}
