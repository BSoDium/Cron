package fr.bsodium.cron.ui.screens.home.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import fr.bsodium.cron.session.model.TriggerType
import fr.bsodium.cron.ui.screens.home.AiIterationUi
import fr.bsodium.cron.ui.screens.home.AiThreadUi
import fr.bsodium.cron.ui.screens.home.RunKind
import fr.bsodium.cron.ui.screens.home.TimelineItem
import fr.bsodium.cron.ui.theme.CronPreview
import fr.bsodium.cron.ui.theme.Spacing
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

private const val PLAYGROUND_SESSION_ID = "playground"

private fun playgroundBaseRun(id: Int, ranAtEpochMs: Long) = TimelineItem.AiRun(
    timestamp = Instant.fromEpochMilliseconds(ranAtEpochMs),
    iteration = AiIterationUi(
        turnIndex = id,
        timeLabel = "23:14",
        kind = RunKind.ScheduledBase,
        thread = AiThreadUi(turnIndex = id, summary = null, process = emptyList(), response = null),
        ranAtEpochMs = ranAtEpochMs,
    ),
    sessionId = PLAYGROUND_SESSION_ID,
    isStreaming = false,
    isLatest = false,
)

private fun playgroundReplanRun(id: Int, ranAtEpochMs: Long, newTime: LocalTime, previousTime: LocalTime) = TimelineItem.AiRun(
    timestamp = Instant.fromEpochMilliseconds(ranAtEpochMs),
    iteration = AiIterationUi(
        turnIndex = id,
        timeLabel = "07:15",
        kind = RunKind.Replan(TriggerType.CalendarChange),
        thread = AiThreadUi(
            turnIndex = id,
            summary = "Moved alarm to $newTime — your first meeting shifted.",
            process = emptyList(),
            response = "Moved alarm to $newTime — your first meeting shifted.",
            newAlarmTime = newTime,
        ),
        previousAlarmTime = previousTime,
        ranAtEpochMs = ranAtEpochMs,
    ),
    sessionId = PLAYGROUND_SESSION_ID,
    isStreaming = false,
    isLatest = true,
)

private fun playgroundEvent(id: Int, epochMs: Long, trigger: TriggerType, label: String) = TimelineItem.Event(
    timestamp = Instant.fromEpochMilliseconds(epochMs),
    trigger = trigger,
    label = label,
    detail = null,
)

private fun playgroundDayHeader(epochMs: Long): TimelineItem.DayHeader {
    val ts = Instant.fromEpochMilliseconds(epochMs)
    return TimelineItem.DayHeader(date = ts.toLocalDateTime(TimeZone.currentSystemDefault()).date, timestamp = ts)
}

/** Promotes [newItem] to Latest and demotes whichever [TimelineItem.AiRun] currently holds that
 *  title — mirrors the real promotion `buildTimeline` performs on every replan, so the playground
 *  reproduces the exact demote+promote interaction Round 41's bug lived in (see
 *  `TimelineAnimationScreenshotTest.a_new_latest_run_never_visually_overlaps_the_run_it_demotes`). */
private fun List<TimelineItem>.promoteToLatest(newItem: TimelineItem.AiRun): List<TimelineItem> =
    listOf(newItem) + map { if (it is TimelineItem.AiRun) it.copy(isLatest = false) else it }

/** An always-available way to throw arbitrary [TimelineItem]s at the real `sessionTimelineItems`
 *  pipeline and watch the resulting entrance/placement/demotion animation live — the gap this exists
 *  to close is that the only on-device trigger for a timeline insertion was a mocked AI replan
 *  (`MockApiPrefs`/`FakeAnthropicClient`), which plays out once, at real wall-clock speed, with no way
 *  to pause or scrub it. Each button below appends one specific kind of item with a fresh id/timestamp
 *  so repeated taps keep stacking distinguishable rows. Point Android Studio's **Animation Inspector**
 *  at this preview (see docs/animation-previews.md) to step through, slow down, or loop the resulting
 *  transition frame by frame instead of eyeballing it once. */
@Preview(name = "Timeline — animation playground", showBackground = true, heightDp = 900, widthDp = 420)
@Composable
private fun TimelineAnimationPlaygroundPreview() {
    CronPreview {
        var nextId by remember { mutableIntStateOf(0) }
        var nextEpochMs by remember { mutableIntStateOf(0) }
        var timeline by remember { mutableStateOf<List<TimelineItem>>(emptyList()) }

        fun tick(): Pair<Int, Long> {
            val id = nextId++
            val epochMs = (nextEpochMs++).toLong()
            return id to epochMs
        }

        val listState = rememberLazyListState()
        val registry = rememberTimelineTrackRegistry()
        val historyItems = emptyPlaygroundHistory()

        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(Spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Button(onClick = {
                    val (id, epochMs) = tick()
                    timeline = listOf(playgroundBaseRun(id, epochMs)) + timeline
                }) { PlaygroundButtonText("+ Base run") }
                Button(onClick = {
                    val (id, epochMs) = tick()
                    timeline = timeline.promoteToLatest(playgroundReplanRun(id, epochMs, newTime = LocalTime(7, 15), previousTime = LocalTime(7, 45)))
                }) { PlaygroundButtonText("+ Replan (promotes)") }
                Button(onClick = {
                    val (id, epochMs) = tick()
                    timeline = listOf(playgroundEvent(id, epochMs, TriggerType.AlarmSnoozed, "Alarm snoozed")) + timeline
                }) { PlaygroundButtonText("+ Snooze event") }
                Button(onClick = {
                    val (id, epochMs) = tick()
                    timeline = listOf(playgroundEvent(id, epochMs, TriggerType.CalendarChange, "Your schedule changed")) + timeline
                }) { PlaygroundButtonText("+ Calendar event") }
                Button(onClick = {
                    val (id, epochMs) = tick()
                    val yesterday = epochMs - 86_400_000L
                    timeline = listOf(
                        playgroundEvent(id, epochMs, TriggerType.OutOfBedConfirmed, "You got up"),
                        playgroundDayHeader(yesterday),
                    ) + timeline
                }) { PlaygroundButtonText("+ New day boundary") }
                Button(onClick = {
                    val (id1, epochMs1) = tick()
                    val (id2, epochMs2) = tick()
                    timeline = timeline.promoteToLatest(playgroundReplanRun(id1, epochMs1, newTime = LocalTime(6, 30), previousTime = LocalTime(7, 15)))
                    timeline = listOf(playgroundEvent(id2, epochMs2, TriggerType.SleepOnset, "You fell asleep")) + timeline
                }) { PlaygroundButtonText("+ Burst (2 at once)") }
                OutlinedButton(onClick = { timeline = timeline.drop(1) }) { PlaygroundButtonText("Remove newest") }
                OutlinedButton(onClick = { timeline = emptyList() }) { PlaygroundButtonText("Reset") }
            }
            Box(modifier = Modifier.weight(1f)) {
                TimelineTrackOverlay(registry = registry, listState = listState)
                LazyColumn(state = listState, modifier = Modifier.padding(horizontal = Spacing.md)) {
                    sessionTimelineItems(
                        liveTimeline = timeline,
                        historyItems = historyItems,
                        registry = registry,
                        onOpenAiRun = { _, _ -> },
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaygroundButtonText(text: String) {
    Text(text, style = MaterialTheme.typography.labelSmall)
}

/** An always-empty, never-loading paged history feed — the playground is only about the live
 *  timeline's own insertion/removal animation, not pagination. */
@Composable
private fun emptyPlaygroundHistory(): LazyPagingItems<TimelineItem> =
    flowOf(
        PagingData.from(
            emptyList<TimelineItem>(),
            LoadStates(
                refresh = LoadState.NotLoading(endOfPaginationReached = false),
                prepend = LoadState.NotLoading(endOfPaginationReached = true),
                append = LoadState.NotLoading(endOfPaginationReached = true),
            ),
        ),
    ).collectAsLazyPagingItems()
