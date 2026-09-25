@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package fr.bsodium.cron.ui.screens.home.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import fr.bsodium.cron.session.model.TriggerType
import fr.bsodium.cron.testutil.captureFilmstrip
import fr.bsodium.cron.testutil.filmstripFrameFile
import fr.bsodium.cron.ui.screens.home.RunKind
import fr.bsodium.cron.ui.screens.home.TimelineItem
import fr.bsodium.cron.ui.theme.CronTheme
import fr.bsodium.cron.ui.theme.Spacing
import kotlin.time.Duration.Companion.hours
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/** Every filmstrip frame this file records is named `$SCREENSHOT_FILE_PREFIX_<scenario>-tXXXms.png`,
 *  distinguishing them from other test classes' frames in the shared `app/build/outputs/roborazzi/`. */
private const val SCREENSHOT_FILE_PREFIX = "TimelineAnimationScreenshotTest"

/** Wires [liveTimeline] through the real `sessionTimelineItems` inside a real `LazyColumn` +
 *  `TimelineTrackOverlay` — the same shape every other test in this package renders through, so an
 *  inserted/removed row plays through the actual `animateItem` choreography, not a flattened stand-in. */
@Composable
private fun TimelineAnimationStage(liveTimeline: List<TimelineItem>, suppressEntranceAnimation: Boolean = false) {
    val listState = rememberLazyListState()
    val registry = rememberTimelineTrackRegistry()
    val historyItems = emptyHistoryItems()
    Box {
        TimelineTrackOverlay(registry = registry, listState = listState)
        LazyColumn(state = listState, modifier = Modifier.padding(horizontal = Spacing.md)) {
            sessionTimelineItems(
                liveTimeline = liveTimeline,
                historyItems = historyItems,
                registry = registry,
                suppressEntranceAnimation = suppressEntranceAnimation,
                onOpenAiRun = { _, _ -> },
            )
        }
    }
}

private fun baseRun(sessionId: String, turn: Int, ranAtEpochMs: Long) = TimelineItem.AiRun(
    timestamp = Instant.fromEpochMilliseconds(ranAtEpochMs),
    iteration = fixedIteration(turn = turn, kind = RunKind.ScheduledBase, summary = null, ranAtEpochMs = ranAtEpochMs),
    sessionId = sessionId,
    isStreaming = false,
    isLatest = false,
)

private fun replanRun(sessionId: String, turn: Int, ranAtEpochMs: Long, newTime: LocalTime, prevTime: LocalTime, isLatest: Boolean) = TimelineItem.AiRun(
    timestamp = Instant.fromEpochMilliseconds(ranAtEpochMs),
    iteration = fixedIteration(
        turn = turn,
        kind = RunKind.Replan(TriggerType.CalendarChange),
        summary = "Moved alarm to $newTime — your first meeting shifted.",
        newAlarmTime = newTime,
        previousAlarmTime = prevTime,
        ranAtEpochMs = ranAtEpochMs,
    ),
    sessionId = sessionId,
    isStreaming = false,
    isLatest = isLatest,
)

@Suppress("DEPRECATION")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
class TimelineAnimationScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    /** The baseline case: one new [TimelineItem.Event] prepended to an already-settled, non-empty
     *  timeline. Every row below it must reflow (fade + `placementSpec` slide), not just appear. */
    @Test
    fun appending_a_single_event_fades_and_slides_the_rest_of_the_timeline_down() {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            CronTheme {
                var timeline by remember {
                    mutableStateOf(
                        listOf(
                            baseRun(sessionId = "s1", turn = 0, ranAtEpochMs = 0L),
                            TimelineItem.Event(timestamp = Instant.fromEpochMilliseconds(-1L), trigger = TriggerType.OutOfBedConfirmed, label = "You got up", detail = null),
                        ),
                    )
                }
                TimelineAnimationStage(timeline)
                LaunchedEffect(Unit) {
                    timeline = listOf(
                        TimelineItem.Event(timestamp = Instant.fromEpochMilliseconds(1L), trigger = TriggerType.AlarmSnoozed, label = "Alarm snoozed", detail = "You get to sleep for 9 extra minutes", detailEmphasis = "9 extra minutes"),
                    ) + timeline
                }
            }
        }
        composeTestRule.captureFilmstrip(SCREENSHOT_FILE_PREFIX, "appending_a_single_event")
    }

    /** Round 41's exact regression, replayed frame-by-frame instead of only at rest (the existing
     *  `SessionTimelineScreenshotTest.a_settled_demoted_run_and_its_successor_never_visually_overlap`
     *  only pins the settled frame): a new Latest [TimelineItem.AiRun] arrives while the previous
     *  Latest is demoted in the same state update. `gatedAnimateItem`'s `justDemoted` snap means the
     *  demoted row must never share a frame with the incoming row's opaque fade-in. */
    @Test
    fun a_new_latest_run_never_visually_overlaps_the_run_it_demotes() {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            CronTheme {
                var timeline by remember {
                    mutableStateOf(
                        listOf(replanRun(sessionId = "s1", turn = 1, ranAtEpochMs = 0L, newTime = LocalTime(7, 45), prevTime = LocalTime(8, 0), isLatest = true)),
                    )
                }
                TimelineAnimationStage(timeline)
                LaunchedEffect(Unit) {
                    timeline = listOf(
                        replanRun(sessionId = "s1", turn = 2, ranAtEpochMs = 1L, newTime = LocalTime(7, 15), prevTime = LocalTime(7, 45), isLatest = true),
                        timeline[0].copy(isLatest = false),
                    )
                }
            }
        }
        composeTestRule.captureFilmstrip(SCREENSHOT_FILE_PREFIX, "new_latest_demotes_previous")
    }

    /** Inserting an item whose local date differs from every currently-rendered item's date must grow
     *  a brand-new [TimelineItem.DayHeader] as part of the same animated insertion (`buildTimeline`'s
     *  `insertDayHeaders` runs upstream of this composable, so the header arrives as an ordinary new
     *  row) — not pop in unanimated ahead of the row it introduces. */
    @Test
    fun inserting_an_item_on_a_new_day_animates_its_day_header_in_alongside_it() {
        composeTestRule.mainClock.autoAdvance = false
        val today = LocalDate(2026, 7, 3)
        val yesterday = LocalDate(2026, 7, 2)
        val tz = TimeZone.currentSystemDefault()
        composeTestRule.setContent {
            CronTheme {
                var timeline by remember {
                    mutableStateOf(
                        listOf(
                            TimelineItem.DayHeader(date = yesterday, timestamp = yesterday.atStartOfDayIn(tz)),
                            TimelineItem.Event(timestamp = yesterday.atStartOfDayIn(tz) + 20.hours, trigger = TriggerType.SleepOnset, label = "You fell asleep", detail = null),
                        ),
                    )
                }
                TimelineAnimationStage(timeline)
                LaunchedEffect(Unit) {
                    timeline = listOf(
                        TimelineItem.DayHeader(date = today, timestamp = today.atStartOfDayIn(tz)),
                        TimelineItem.Event(timestamp = today.atStartOfDayIn(tz) + 7.hours, trigger = TriggerType.OutOfBedConfirmed, label = "You got up", detail = null),
                    ) + timeline
                }
            }
        }
        composeTestRule.captureFilmstrip(SCREENSHOT_FILE_PREFIX, "new_day_header_with_item")
    }

    /** The riskiest ordering bug for a reflow animation: a second insertion lands while the first
     *  insertion's placement/fade is still mid-flight, before `fastSpatialSpec`/`fastEffectsSpec` have
     *  settled. Every row's `animateItem` must retarget smoothly from wherever it currently sits, not
     *  snap or restart from the pre-first-insertion layout. A single `ComposeContentTestRule` only
     *  allows one `setContent` call per test, so both insertions are driven by mutating a plain
     *  (non-`remember`ed) `MutableState` held here in the test body between two `mainClock` advances,
     *  rather than a second `setContent`. */
    @Test
    fun a_second_insertion_mid_flight_of_the_first_retargets_smoothly_without_snapping() {
        composeTestRule.mainClock.autoAdvance = false
        val timelineState = mutableStateOf<List<TimelineItem>>(listOf(baseRun(sessionId = "s1", turn = 0, ranAtEpochMs = 0L)))
        composeTestRule.setContent {
            CronTheme { TimelineAnimationStage(timelineState.value) }
        }
        timelineState.value = listOf(
            TimelineItem.Event(timestamp = Instant.fromEpochMilliseconds(1L), trigger = TriggerType.AlarmDismissed, label = "Alarm dismissed", detail = null),
        ) + timelineState.value
        // Flush the mutation into the tree (see captureFilmstrip's KDoc) before timing its own animation.
        composeTestRule.waitForIdle()
        // Only 40ms into the first insertion's own animation — well before fastSpatialSpec settles — before the second insertion lands.
        composeTestRule.mainClock.advanceTimeBy(40L)
        composeTestRule.onRoot().captureRoboImage(filmstripFrameFile(SCREENSHOT_FILE_PREFIX, "rapid_double_insertion-first-mid-flight-t40ms"))
        timelineState.value = listOf(
            TimelineItem.Event(timestamp = Instant.fromEpochMilliseconds(2L), trigger = TriggerType.SleepOnset, label = "You fell asleep", detail = null),
        ) + timelineState.value
        // The mutation above already landed directly (no LaunchedEffect pending), so priming again here would double-count a frame — see captureFilmstrip's KDoc.
        composeTestRule.captureFilmstrip(SCREENSHOT_FILE_PREFIX, "rapid_double_insertion-second", primeForLaunchedEffect = false)
    }

    /** `firstAnchorIndex`/`lastAnchorIndex` in `sessionTimelineItems` are derived with
     *  `indexOfFirst`/`indexOfLast`, both of which return -1 for an empty list — the very first item
     *  ever added to a brand-new session (no plan yet) is the one case exercising that transition, and
     *  must still land as a proper segment cap on both ends rather than an un-capped interior row. */
    @Test
    fun the_first_item_ever_added_to_an_empty_timeline_animates_in_as_a_capped_segment() {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            CronTheme {
                var timeline by remember { mutableStateOf(emptyList<TimelineItem>()) }
                TimelineAnimationStage(timeline)
                LaunchedEffect(Unit) {
                    timeline = listOf(baseRun(sessionId = "s1", turn = 0, ranAtEpochMs = 0L))
                }
            }
        }
        composeTestRule.captureFilmstrip(SCREENSHOT_FILE_PREFIX, "first_item_into_empty_timeline")
    }

    /** `HomeContent.kt`'s `rememberTimelineSettled` forces `suppressEntranceAnimation = true` while a
     *  fresh mount (cold start, or a Home→Settings→back round trip) is still settling, so unchanged
     *  data never replays an entrance. This pins the handoff: a row added *while still suppressed*
     *  must appear instantly (no frame with partial opacity/offset), then a row added *after* the
     *  suppression lifts must animate normally — the two must never bleed into each other. Both phases
     *  are driven from one `setContent` (see the previous test's KDoc for why) via plain `MutableState`
     *  held in the test body, mutated directly between `mainClock` advances. */
    @Test
    fun suppressed_insertions_snap_in_instantly_then_resume_animating_once_settled() {
        composeTestRule.mainClock.autoAdvance = false
        val suppressedState = mutableStateOf(true)
        val timelineState = mutableStateOf<List<TimelineItem>>(listOf(baseRun(sessionId = "s1", turn = 0, ranAtEpochMs = 0L)))
        composeTestRule.setContent {
            CronTheme { TimelineAnimationStage(timelineState.value, suppressEntranceAnimation = suppressedState.value) }
        }
        // Inserted while still suppressed (mirrors cold-start settle) — gatedAnimateItem forces every spec to null, so this must snap in with no animated frame at all.
        timelineState.value = listOf(
            TimelineItem.Event(timestamp = Instant.fromEpochMilliseconds(1L), trigger = TriggerType.AlarmDismissed, label = "Alarm dismissed", detail = null),
        ) + timelineState.value
        // Flush the mutation into the tree (see captureFilmstrip's KDoc).
        composeTestRule.waitForIdle()
        composeTestRule.mainClock.advanceTimeBy(16L)
        composeTestRule.onRoot().captureRoboImage(filmstripFrameFile(SCREENSHOT_FILE_PREFIX, "suppression_handoff-t0-suppressed-insert"))
        // Suppression lifts (mirrors rememberTimelineSettled clearing once the mount has settled).
        suppressedState.value = false
        composeTestRule.waitForIdle()
        composeTestRule.mainClock.advanceTimeBy(500L)
        // Inserted once settled — must animate normally.
        timelineState.value = listOf(
            TimelineItem.Event(timestamp = Instant.fromEpochMilliseconds(2L), trigger = TriggerType.SleepOnset, label = "You fell asleep", detail = null),
        ) + timelineState.value
        // The mutation above already landed directly (no LaunchedEffect pending), so priming again here would double-count a frame — see captureFilmstrip's KDoc.
        composeTestRule.captureFilmstrip(SCREENSHOT_FILE_PREFIX, "suppression_handoff-resumed", primeForLaunchedEffect = false)
    }

    /** A real replan turn commonly produces two rows at once (the [TimelineItem.AiRun] plus the
     *  [TimelineItem.Event] that triggered it) — both must animate in independently rather than one
     *  masking or racing the other. */
    @Test
    fun a_burst_of_two_simultaneous_insertions_animates_both_independently() {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            CronTheme {
                var timeline by remember {
                    mutableStateOf<List<TimelineItem>>(
                        listOf(baseRun(sessionId = "s1", turn = 0, ranAtEpochMs = 0L)),
                    )
                }
                TimelineAnimationStage(timeline)
                LaunchedEffect(Unit) {
                    timeline = listOf(
                        replanRun(sessionId = "s1", turn = 1, ranAtEpochMs = 2L, newTime = LocalTime(7, 15), prevTime = LocalTime(7, 45), isLatest = true),
                        TimelineItem.Event(timestamp = Instant.fromEpochMilliseconds(1L), trigger = TriggerType.CalendarChange, label = "Your schedule changed", detail = null),
                        (timeline[0] as TimelineItem.AiRun).copy(isLatest = false),
                    )
                }
            }
        }
        composeTestRule.captureFilmstrip(SCREENSHOT_FILE_PREFIX, "burst_of_two_insertions")
    }

    /** The other half of `gatedAnimateItem`'s spec (`fadeOutSpec`): removing the newest row must fade
     *  it out and slide the remainder up, not disappear it instantly. */
    @Test
    fun removing_the_newest_item_fades_it_out_and_reflows_the_remainder_upward() {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            CronTheme {
                var timeline by remember {
                    mutableStateOf(
                        listOf(
                            TimelineItem.Event(timestamp = Instant.fromEpochMilliseconds(2L), trigger = TriggerType.AlarmDismissed, label = "Alarm dismissed", detail = null),
                            baseRun(sessionId = "s1", turn = 0, ranAtEpochMs = 0L),
                        ),
                    )
                }
                TimelineAnimationStage(timeline)
                LaunchedEffect(Unit) {
                    timeline = timeline.drop(1)
                }
            }
        }
        composeTestRule.captureFilmstrip(SCREENSHOT_FILE_PREFIX, "removing_newest_item")
    }
}
