package fr.bsodium.cron.ui.screens.home.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import fr.bsodium.cron.session.model.TriggerType
import fr.bsodium.cron.ui.screens.home.AiIterationUi
import fr.bsodium.cron.ui.screens.home.AiThreadUi
import fr.bsodium.cron.ui.screens.home.ProcessItem
import fr.bsodium.cron.ui.screens.home.RunKind
import fr.bsodium.cron.ui.screens.home.TimelineItem
import fr.bsodium.cron.ui.theme.CronTheme
import fr.bsodium.cron.ui.theme.Spacing
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

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

private fun previewDayHeader(dateOffsetDays: Int, weekdayLabel: String, dateLabel: String): TimelineItem.DayHeader {
    val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    return TimelineItem.DayHeader(
        date = today.plus(dateOffsetDays, DateTimeUnit.DAY),
        timestamp = Instant.fromEpochMilliseconds(System.currentTimeMillis()),
        weekdayLabel = weekdayLabel,
        dateLabel = dateLabel,
    )
}

@Preview(showBackground = true, name = "Timeline — full example")
@Composable
private fun SessionTimelinePreview() {
    val now = Instant.fromEpochMilliseconds(System.currentTimeMillis())
    val yesterday = Instant.fromEpochMilliseconds(System.currentTimeMillis() - 86_400_000L)
    val twoDaysAgo = Instant.fromEpochMilliseconds(System.currentTimeMillis() - 172_800_000L)
    val timeline = listOf(
        previewDayHeader(0, "TODAY", "3 JUL"),
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
        previewDayHeader(-1, "YESTERDAY", "2 JUL"),
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
        previewDayHeader(-2, "MONDAY", "1 JUL"),
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
                val isLast = index == timeline.lastIndex || timeline[index + 1] is TimelineItem.DayHeader
                when (item) {
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
                    is TimelineItem.DayHeader -> DayHeaderRow(item = item)
                }
            }
        }
    }
}
