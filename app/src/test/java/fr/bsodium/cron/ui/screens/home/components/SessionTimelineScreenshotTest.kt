@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package fr.bsodium.cron.ui.screens.home.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import com.github.takahirom.roborazzi.captureRoboImage
import fr.bsodium.cron.session.model.TriggerType
import fr.bsodium.cron.ui.screens.home.AiIterationUi
import fr.bsodium.cron.ui.screens.home.AiThreadUi
import fr.bsodium.cron.ui.screens.home.ProcessItem
import fr.bsodium.cron.ui.screens.home.RunKind
import fr.bsodium.cron.ui.screens.home.TimelineItem
import fr.bsodium.cron.ui.screens.home.timelineAsleepStates
import fr.bsodium.cron.ui.theme.CronTheme
import fr.bsodium.cron.ui.theme.Spacing
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import org.junit.Rule
import org.junit.Test
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import org.junit.runner.RunWith
import kotlin.time.Duration.Companion.hours

private fun fixedIteration(
    turn: Int,
    kind: RunKind,
    summary: String?,
    process: List<ProcessItem> = emptyList(),
    newAlarmTime: LocalTime? = null,
    previousAlarmTime: LocalTime? = null,
) = AiIterationUi(
    turnIndex = turn,
    timeLabel = "23:14",
    kind = kind,
    thread = AiThreadUi(turnIndex = turn, summary = summary, process = process, response = summary, newAlarmTime = newAlarmTime),
    previousAlarmTime = previousAlarmTime,
)

@Suppress("DEPRECATION")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
class SessionTimelineScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun timeline_spans_two_days_with_hero_run_and_accented_events() {
        composeTestRule.mainClock.autoAdvance = false
        val timeline = listOf(
            TimelineItem.DayHeader(date = LocalDate(2026, 7, 3), timestamp = Instant.fromEpochMilliseconds(0L)),
            TimelineItem.AiRun(
                timestamp = Instant.fromEpochMilliseconds(0L),
                iteration = fixedIteration(
                    turn = 1,
                    kind = RunKind.Replan(TriggerType.CalendarChange),
                    summary = "Moved alarm to 07:15 — your first meeting shifted to 09:00.",
                    process = listOf(
                        ProcessItem.Reasoning("Checking calendar for changes..."),
                        ProcessItem.Tool("read_calendar", isComplete = true, contextLabel = "3 events"),
                        ProcessItem.Tool("set_alarm", isComplete = true, contextLabel = "07:15"),
                    ),
                    newAlarmTime = LocalTime(7, 15),
                    previousAlarmTime = LocalTime(7, 45),
                ),
                sessionId = "s1",
                isStreaming = false,
                isLatest = true,
            ),
            TimelineItem.Event(
                timestamp = Instant.fromEpochMilliseconds(0L),
                trigger = TriggerType.AlarmSnoozed,
                label = "Alarm snoozed",
                detail = "You get to sleep for 9 extra minutes",
                detailEmphasis = "9 extra minutes",
            ),
            TimelineItem.Event(
                timestamp = Instant.fromEpochMilliseconds(0L),
                trigger = TriggerType.SleepOnset,
                label = "You fell asleep",
                detail = null,
            ),
            TimelineItem.DayHeader(date = LocalDate(2026, 7, 2), timestamp = Instant.fromEpochMilliseconds(0L)),
            TimelineItem.Event(
                timestamp = Instant.fromEpochMilliseconds(0L),
                trigger = TriggerType.OutOfBedConfirmed,
                label = "You got up",
                detail = null,
            ),
            TimelineItem.AiRun(
                timestamp = Instant.fromEpochMilliseconds(0L),
                iteration = fixedIteration(turn = 0, kind = RunKind.ScheduledBase, summary = null),
                sessionId = "s2",
                isStreaming = false,
                isLatest = false,
            ),
        )
        composeTestRule.setContent {
            CronTheme { TimelineTestContent(timeline) }
        }
        // Let the hero anchor's arrival morph settle before capturing.
        composeTestRule.mainClock.advanceTimeBy(1_000L)
        composeTestRule.onRoot().captureRoboImage()
    }

    /** A day boundary must never split a sleep session's pill/segment. The `DayHeader` sits right
     *  where midnight would fall — between "Movement detected" (late "yesterday") and "You got up"
     *  (early "today") — and must not affect segment/cap/asleep-state derivation; the whole stretch
     *  should render as one uninterrupted dark pill with no cap/seam. */
    @Test
    fun sleep_session_spanning_midnight_renders_one_continuous_pill() {
        composeTestRule.mainClock.autoAdvance = false
        val timeline = listOf(
            TimelineItem.Event(
                timestamp = Instant.fromEpochMilliseconds(0L),
                trigger = TriggerType.OutOfBedConfirmed,
                label = "You got up",
                detail = null,
            ),
            TimelineItem.DayHeader(date = LocalDate(2026, 7, 3), timestamp = Instant.fromEpochMilliseconds(0L)),
            TimelineItem.Event(
                timestamp = Instant.fromEpochMilliseconds(0L),
                trigger = TriggerType.MidSleepActivity,
                label = "Movement detected",
                detail = null,
            ),
            TimelineItem.Event(
                timestamp = Instant.fromEpochMilliseconds(0L),
                trigger = TriggerType.SleepOnset,
                label = "You fell asleep",
                detail = null,
            ),
            TimelineItem.Event(
                timestamp = Instant.fromEpochMilliseconds(0L),
                trigger = TriggerType.CalendarChange,
                label = "Your schedule changed",
                detail = null,
            ),
        )
        composeTestRule.setContent {
            CronTheme { TimelineTestContent(timeline) }
        }
        composeTestRule.mainClock.advanceTimeBy(1_000L)
        composeTestRule.onRoot().captureRoboImage()
    }

    /** A static single-frame screenshot can't show scroll dynamics, so this drives a real
     *  `LazyColumn` (via the production `sessionTimelineItems`, not the flattened
     *  `TimelineTestContent` used elsewhere in this file) with a real `LazyListState`, scrolls
     *  programmatically past a `DayHeader`'s natural position, and captures the result — every
     *  header renders identically regardless of scroll position (see [DayHeaderRow]'s KDoc), so this
     *  is a render-correctness regression check for the scrolled-past state, not a differential
     *  active/inactive emphasis check. */
    @Test
    fun day_header_emphasizes_the_active_section_while_scrolled_past_it() {
        composeTestRule.mainClock.autoAdvance = false
        val timeline = buildList {
            add(TimelineItem.DayHeader(date = LocalDate(2026, 7, 6), timestamp = Instant.fromEpochMilliseconds(0L)))
            for (i in 0 until 20) {
                add(
                    TimelineItem.Event(
                        timestamp = Instant.fromEpochMilliseconds((20 - i).toLong()),
                        trigger = TriggerType.AlarmDismissed,
                        label = "Event $i",
                        detail = null,
                    ),
                )
            }
            add(TimelineItem.DayHeader(date = LocalDate(2026, 7, 5), timestamp = Instant.fromEpochMilliseconds(0L)))
            add(TimelineItem.Event(timestamp = Instant.fromEpochMilliseconds(0L), trigger = TriggerType.AlarmDismissed, label = "Older event", detail = null))
        }
        composeTestRule.setContent {
            CronTheme {
                val listState = rememberLazyListState()
                val registry = rememberTimelineTrackRegistry()
                val historyItems = emptyHistoryItems()
                // Index 22 is DayHeader "Yesterday" (after the top spacer, "Today" header, and 20 events); scrolling there puts it at the viewport top with "Today" scrolled fully past.
                LaunchedEffect(Unit) { listState.scrollToItem(index = 22) }
                Box {
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
        composeTestRule.mainClock.advanceTimeBy(1_000L)
        composeTestRule.onRoot().captureRoboImage()
    }

    /** Regression for #193: the hero headline (`thread.summary`) is model-authored text and the
     *  system prompt promises it full Markdown — this must actually render as Markdown (bold time
     *  visibly bold), not literal asterisks via a plain Text. */
    @Test
    fun hero_headline_renders_markdown_not_literal_asterisks() {
        composeTestRule.mainClock.autoAdvance = false
        val timeline = listOf(
            TimelineItem.AiRun(
                timestamp = Instant.fromEpochMilliseconds(0L),
                iteration = fixedIteration(
                    turn = 0,
                    kind = RunKind.Replan(TriggerType.CalendarChange),
                    summary = "Calendar changed — replanned to **13:20**, first anchor is now Train IR 1633.",
                    newAlarmTime = LocalTime(13, 20),
                    previousAlarmTime = LocalTime(9, 30),
                ),
                sessionId = "s1",
                isStreaming = false,
                isLatest = true,
            ),
        )
        composeTestRule.setContent {
            CronTheme { TimelineTestContent(timeline) }
        }
        composeTestRule.mainClock.advanceTimeBy(1_000L)
        composeTestRule.onRoot().captureRoboImage()
    }

    /** `suppressEntranceAnimation = true` mirrors the settled end-state a demoted row and its
     *  successor reach once both have finished reflowing (also what a Home→Settings→back round trip
     *  renders immediately) via the real `sessionTimelineItems` — capturing that resting frame
     *  (rather than a static `TimelineTestContent` flattening) exercises `AiRunNode`/`TimelineNode`'s
     *  actual rendering path end to end, to guard against the two rows' content ever being
     *  simultaneously fully opaque and visually superimposed. */
    @Test
    fun a_settled_demoted_run_and_its_successor_never_visually_overlap() {
        composeTestRule.mainClock.autoAdvance = false
        val timeline = listOf(
            TimelineItem.AiRun(
                timestamp = Instant.fromEpochMilliseconds(1L),
                iteration = fixedIteration(
                    turn = 1,
                    kind = RunKind.Replan(TriggerType.CalendarChange),
                    summary = "Moved alarm to 07:15 — your first meeting shifted to 09:00.",
                    newAlarmTime = LocalTime(7, 15),
                    previousAlarmTime = LocalTime(7, 45),
                ),
                sessionId = "s1",
                isStreaming = false,
                isLatest = true,
            ),
            TimelineItem.AiRun(
                timestamp = Instant.fromEpochMilliseconds(0L),
                iteration = fixedIteration(turn = 0, kind = RunKind.ScheduledBase, summary = null),
                sessionId = "s1",
                isStreaming = false,
                isLatest = false,
            ),
        )
        composeTestRule.setContent {
            CronTheme {
                val listState = rememberLazyListState()
                val registry = rememberTimelineTrackRegistry()
                val historyItems = emptyHistoryItems()
                Box {
                    TimelineTrackOverlay(registry = registry, listState = listState)
                    LazyColumn(state = listState, modifier = Modifier.padding(horizontal = Spacing.md)) {
                        sessionTimelineItems(
                            liveTimeline = timeline,
                            historyItems = historyItems,
                            registry = registry,
                            suppressEntranceAnimation = true,
                            onOpenAiRun = { _, _ -> },
                        )
                    }
                }
            }
        }
        composeTestRule.mainClock.advanceTimeBy(1_000L)
        composeTestRule.onRoot().captureRoboImage()
    }

    /** [androidx.paging.LoadState.Loading] on the history feed's append edge — the replacement for the
     *  old "View full history" dead-end button (#187/#231). */
    @Test
    fun append_loading_row_renders_while_more_history_is_loading() {
        composeTestRule.mainClock.autoAdvance = false
        val live = listOf(
            TimelineItem.Event(timestamp = Instant.fromEpochMilliseconds(0L), trigger = TriggerType.AlarmDismissed, label = "Alarm dismissed", detail = null),
        )
        composeTestRule.setContent {
            CronTheme {
                val listState = rememberLazyListState()
                val registry = rememberTimelineTrackRegistry()
                val historyItems = pagedHistoryItems(emptyList(), appendState = LoadState.Loading)
                Box {
                    TimelineTrackOverlay(registry = registry, listState = listState)
                    LazyColumn(state = listState, modifier = Modifier.padding(horizontal = Spacing.md)) {
                        sessionTimelineItems(
                            liveTimeline = live,
                            historyItems = historyItems,
                            registry = registry,
                            onOpenAiRun = { _, _ -> },
                        )
                    }
                }
            }
        }
        composeTestRule.mainClock.advanceTimeBy(1_000L)
        composeTestRule.onRoot().captureRoboImage()
    }

    /** [androidx.paging.LoadState.Error] on the history feed's append edge, with its retry affordance. */
    @Test
    fun append_error_row_renders_with_a_retry_affordance() {
        composeTestRule.mainClock.autoAdvance = false
        val live = listOf(
            TimelineItem.Event(timestamp = Instant.fromEpochMilliseconds(0L), trigger = TriggerType.AlarmDismissed, label = "Alarm dismissed", detail = null),
        )
        composeTestRule.setContent {
            CronTheme {
                val listState = rememberLazyListState()
                val registry = rememberTimelineTrackRegistry()
                val historyItems = pagedHistoryItems(emptyList(), appendState = LoadState.Error(RuntimeException("boom")))
                Box {
                    TimelineTrackOverlay(registry = registry, listState = listState)
                    LazyColumn(state = listState, modifier = Modifier.padding(horizontal = Spacing.md)) {
                        sessionTimelineItems(
                            liveTimeline = live,
                            historyItems = historyItems,
                            registry = registry,
                            onOpenAiRun = { _, _ -> },
                        )
                    }
                }
            }
        }
        composeTestRule.mainClock.advanceTimeBy(1_000L)
        composeTestRule.onRoot().captureRoboImage()
    }

    /** The live-session→history boundary is the one day-header case `TimelineRepository`'s own
     *  `insertSeparators` deliberately never emits (see `historyDaySeparator`'s KDoc) — `seamDayHeader`
     *  owns it here instead. This pins that it renders exactly once between a live item and the first
     *  historical item on a different (non-today) date, not zero times and not twice. */
    @Test
    fun seam_day_header_renders_once_between_live_and_history_on_a_date_change() {
        composeTestRule.mainClock.autoAdvance = false
        val live = listOf(
            TimelineItem.Event(
                timestamp = LocalDate(2026, 7, 3).atStartOfDayIn(TimeZone.currentSystemDefault()) + 6.hours,
                trigger = TriggerType.AlarmDismissed,
                label = "Alarm dismissed",
                detail = null,
            ),
        )
        val historicalFirst = TimelineItem.Event(
            timestamp = LocalDate(2026, 7, 2).atStartOfDayIn(TimeZone.currentSystemDefault()) + 20.hours,
            trigger = TriggerType.SleepOnset,
            label = "You fell asleep",
            detail = null,
        )
        composeTestRule.setContent {
            CronTheme {
                val listState = rememberLazyListState()
                val registry = rememberTimelineTrackRegistry()
                val historyItems = pagedHistoryItems(listOf(historicalFirst), appendState = LoadState.NotLoading(endOfPaginationReached = true))
                Box {
                    TimelineTrackOverlay(registry = registry, listState = listState)
                    LazyColumn(state = listState, modifier = Modifier.padding(horizontal = Spacing.md)) {
                        sessionTimelineItems(
                            liveTimeline = live,
                            historyItems = historyItems,
                            registry = registry,
                            onOpenAiRun = { _, _ -> },
                        )
                    }
                }
            }
        }
        composeTestRule.mainClock.advanceTimeBy(1_000L)
        composeTestRule.onRoot().captureRoboImage()
    }
}

/** An always-empty, never-loading paged history feed — these tests are about the live timeline's own
 *  rendering, not pagination, so `sessionTimelineItems`' paged tail contributes nothing here. */
@Composable
private fun emptyHistoryItems(): LazyPagingItems<TimelineItem> =
    pagedHistoryItems(emptyList(), appendState = LoadState.NotLoading(endOfPaginationReached = true))

/** A static (non-loading, unless [appendState] says otherwise) paged history feed carrying exactly
 *  [items] — for exercising `sessionTimelineItems`' paged tail (the append-loading/error rows, the
 *  live/history seam header) without a real Pager/Room round trip. */
@Composable
private fun pagedHistoryItems(items: List<TimelineItem>, appendState: LoadState): LazyPagingItems<TimelineItem> =
    flowOf(
        PagingData.from(
            items,
            LoadStates(
                refresh = LoadState.NotLoading(endOfPaginationReached = false),
                prepend = LoadState.NotLoading(endOfPaginationReached = true),
                append = appendState,
            ),
        ),
    ).collectAsLazyPagingItems()

/** Mirrors `sessionTimelineItems`' per-row flag derivation (single continuous segment, list-ends-only
 *  caps) without the real LazyColumn — a golden-screenshot harness needs deterministic content, not
 *  live scroll state. */
@Composable
private fun TimelineTestContent(timeline: List<TimelineItem>) {
    val asleepStates = timelineAsleepStates(timeline)
    val firstAnchorIndex = timeline.indexOfFirst { it !is TimelineItem.DayHeader }
    val lastAnchorIndex = timeline.indexOfLast { it !is TimelineItem.DayHeader }
    val registry = rememberTimelineTrackRegistry()
    // The static harness resolves anchors through TimelineTrackOverlay's live-handle fallback.
    val listState = rememberLazyListState()
    Box {
        TimelineTrackOverlay(registry = registry, listState = listState)
        Column(modifier = Modifier.padding(horizontal = Spacing.md)) {
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
