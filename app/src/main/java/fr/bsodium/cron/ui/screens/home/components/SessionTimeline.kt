package fr.bsodium.cron.ui.screens.home.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import fr.bsodium.cron.session.model.TriggerType
import fr.bsodium.cron.ui.screens.home.AiIterationUi
import fr.bsodium.cron.ui.screens.home.AiThreadUi
import fr.bsodium.cron.ui.screens.home.RunKind
import fr.bsodium.cron.ui.screens.home.TimelineItem
import fr.bsodium.cron.ui.theme.CronTheme
import fr.bsodium.cron.ui.theme.Spacing
import kotlinx.datetime.Instant

internal fun LazyListScope.sessionTimelineItems(
    timeline: List<TimelineItem>,
    hasMore: Boolean,
    onOpenAiRun: (turnIndex: Int, sessionId: String) -> Unit,
    onLoadMore: () -> Unit,
) {
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
            )
            is TimelineItem.AiRun -> PlanTimelineCard(
                iteration = item.iteration,
                isLatest = item.isLatest,
                isStreaming = item.isStreaming,
                isFirst = isFirst,
                isLast = isLast,
                onClick = { onOpenAiRun(item.iteration.turnIndex, item.sessionId) },
            )
            is TimelineItem.Event -> EventTimelineCard(
                trigger = item.trigger,
                label = item.label,
                detail = item.detail,
                timestamp = item.timestamp,
                isFirst = isFirst,
                isLast = isLast,
            )
        }
    }

    if (hasMore) {
        item(key = "load-more") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Spacing.md),
                contentAlignment = Alignment.Center,
            ) {
                TextButton(onClick = onLoadMore) {
                    Text("Load more")
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SessionTimelinePreview() {
    val now = Instant.fromEpochMilliseconds(System.currentTimeMillis())
    val timeline = listOf(
        TimelineItem.DayHeader(timestamp = now, label = "Today"),
        TimelineItem.AiRun(
            timestamp = now,
            iteration = AiIterationUi(
                turnIndex = 0,
                timeLabel = "23:14",
                kind = RunKind.ScheduledBase,
                thread = AiThreadUi(0, "Thought for 8s", emptyList(), "Set alarm for 7:15."),
                ranAtEpochMs = now.toEpochMilliseconds(),
            ),
            sessionId = "s1",
            isStreaming = false,
            isLatest = true,
        ),
        TimelineItem.Event(
            timestamp = now,
            trigger = TriggerType.SleepOnset,
            label = "You fell asleep",
            detail = null,
        ),
        TimelineItem.AiRun(
            timestamp = now,
            iteration = AiIterationUi(
                turnIndex = 1,
                timeLabel = "21:30",
                kind = RunKind.Replan(TriggerType.CalendarChange),
                thread = AiThreadUi(1, null, emptyList(), "Your first meeting moved to 10:00."),
                ranAtEpochMs = now.toEpochMilliseconds(),
            ),
            sessionId = "s1",
            isStreaming = false,
            isLatest = false,
        ),
    )
    CronTheme {
        Column(modifier = Modifier.padding(Spacing.lg)) {
            timeline.forEachIndexed { index, item ->
                val isFirst = index == 0 || timeline[index - 1] is TimelineItem.DayHeader
                val isLast = index == timeline.lastIndex || timeline.getOrNull(index + 1) is TimelineItem.DayHeader
                when (item) {
                    is TimelineItem.DayHeader -> SessionTimelineDayHeader(
                        label = item.label,
                        isFirst = index == 0,
                        isLast = index == timeline.lastIndex,
                    )
                    is TimelineItem.AiRun -> PlanTimelineCard(
                        iteration = item.iteration,
                        isLatest = item.isLatest,
                        isStreaming = item.isStreaming,
                        isFirst = isFirst,
                        isLast = isLast,
                        onClick = {},
                    )
                    is TimelineItem.Event -> EventTimelineCard(
                        trigger = item.trigger,
                        label = item.label,
                        detail = item.detail,
                        timestamp = item.timestamp,
                        isFirst = isFirst,
                        isLast = isLast,
                    )
                }
            }
        }
    }
}
