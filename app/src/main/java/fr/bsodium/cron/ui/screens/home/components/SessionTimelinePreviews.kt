package fr.bsodium.cron.ui.screens.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import fr.bsodium.cron.session.model.TriggerType
import fr.bsodium.cron.ui.screens.home.AiIterationUi
import fr.bsodium.cron.ui.screens.home.AiThreadUi
import fr.bsodium.cron.ui.screens.home.ProcessItem
import fr.bsodium.cron.ui.screens.home.RunKind
import fr.bsodium.cron.ui.screens.home.TimelineItem
import fr.bsodium.cron.ui.screens.home.timelineAsleepStates
import fr.bsodium.cron.ui.theme.CronColors
import fr.bsodium.cron.ui.theme.CronTheme
import fr.bsodium.cron.ui.theme.Spacing
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

private fun previewDayHeader(dateOffsetDays: Int): TimelineItem.DayHeader {
    val ts = Instant.fromEpochMilliseconds(System.currentTimeMillis() - dateOffsetDays * 86_400_000L)
    return TimelineItem.DayHeader(date = ts.toLocalDateTime(TimeZone.currentSystemDefault()).date, timestamp = ts)
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

@PreviewLightDark
@Composable
private fun SessionTimelinePreview() {
    val now = Instant.fromEpochMilliseconds(System.currentTimeMillis())
    val yesterday = Instant.fromEpochMilliseconds(System.currentTimeMillis() - 86_400_000L)
    val twoDaysAgo = Instant.fromEpochMilliseconds(System.currentTimeMillis() - 172_800_000L)
    val timeline = listOf(
        previewDayHeader(0),
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
            detail = "You get to sleep for 9 extra minutes",
            detailEmphasis = "9 extra minutes",
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
        previewDayHeader(1),
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
        previewDayHeader(2),
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
    val asleepStates = timelineAsleepStates(timeline)
    val firstAnchorIndex = timeline.indexOfFirst { it !is TimelineItem.DayHeader }
    val lastAnchorIndex = timeline.indexOfLast { it !is TimelineItem.DayHeader }
    CronTheme {
        val registry = rememberTimelineTrackRegistry()
        Box(modifier = Modifier.fillMaxSize().background(CronColors.pageBackground)) {
            TimelineTrackOverlay(registry = registry)
            Column(modifier = Modifier.padding(horizontal = Spacing.xl)) {
                timeline.forEachIndexed { index, item ->
                    val isSegmentTop = index == firstAnchorIndex
                    val isSegmentBottom = index == lastAnchorIndex
                    val isAsleepAbove = asleepStates[index]
                    val isAsleepBelow = asleepStates.getOrNull(index + 1) ?: asleepStates[index]
                    when (item) {
                        is TimelineItem.AiRun -> AiRunNode(
                            item = item,
                            registry = registry,
                            isSegmentTop = isSegmentTop,
                            isSegmentBottom = isSegmentBottom,
                            isAsleepAbove = isAsleepAbove,
                            isAsleepBelow = isAsleepBelow,
                            onClick = {},
                        )
                        is TimelineItem.Event -> EventNode(
                            item = item,
                            registry = registry,
                            isSegmentTop = isSegmentTop,
                            isSegmentBottom = isSegmentBottom,
                            isAsleepAbove = isAsleepAbove,
                            isAsleepBelow = isAsleepBelow,
                        )
                        is TimelineItem.DayHeader -> DayHeaderRow(item = item)
                    }
                }
            }
        }
    }
}

/** Interactive: tap to toggle the run between latest (hero PREV › NEW) and superseded (plain row) so
 *  the Animation Inspector can scrub the `ai-run-hero-demote` crossfade + the anchor's radius shrink.
 *  See docs/animation-previews.md's interactive-preview pattern. */
@PreviewLightDark
@Composable
private fun AiRunNodeHeroDemotePreview() {
    val now = Instant.fromEpochMilliseconds(System.currentTimeMillis())
    CronTheme {
        var isLatest by remember { mutableStateOf(true) }
        val registry = rememberTimelineTrackRegistry()
        Box(modifier = Modifier.fillMaxSize().background(CronColors.pageBackground).clickable { isLatest = !isLatest }) {
            TimelineTrackOverlay(registry = registry)
            Column(modifier = Modifier.padding(horizontal = Spacing.xl)) {
                AiRunNode(
                    item = TimelineItem.AiRun(
                        timestamp = now,
                        iteration = AiIterationUi(
                            turnIndex = 2,
                            timeLabel = "07:15",
                            kind = RunKind.Replan(TriggerType.CalendarChange),
                            thread = AiThreadUi(
                                turnIndex = 2,
                                summary = "Moved alarm to 07:15 — your first meeting shifted to 09:00.",
                                process = emptyList(),
                                response = "Moved alarm to 07:15 — your first meeting shifted to 09:00.",
                                newAlarmTime = LocalTime(7, 15),
                            ),
                            previousAlarmTime = LocalTime(7, 45),
                            ranAtEpochMs = System.currentTimeMillis(),
                        ),
                        sessionId = "s1",
                        isStreaming = false,
                        isLatest = isLatest,
                    ),
                    registry = registry,
                    isSegmentTop = true,
                    isSegmentBottom = true,
                    isAsleepAbove = false,
                    isAsleepBelow = false,
                    onClick = {},
                )
            }
        }
    }
}
