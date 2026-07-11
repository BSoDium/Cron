package fr.bsodium.cron.ui.screens.home

import fr.bsodium.cron.session.model.EventData
import fr.bsodium.cron.session.model.SessionEvent
import fr.bsodium.cron.session.model.TriggerType
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.hours

class TimelineMapperTest {

    private fun events(count: Int): List<TimelineItem> = (0 until count).map { i ->
        TimelineItem.Event(
            timestamp = Instant.fromEpochMilliseconds(i.toLong()),
            trigger = TriggerType.AlarmDismissed,
            label = "Event $i",
            detail = null,
        )
    }

    private fun dayHeader(date: LocalDate) = TimelineItem.DayHeader(
        date = date,
        timestamp = Instant.fromEpochMilliseconds(0),
    )

    private fun event(ms: Long, trigger: TriggerType) = TimelineItem.Event(
        timestamp = Instant.fromEpochMilliseconds(ms),
        trigger = trigger,
        label = trigger.name,
        detail = null,
    )

    private fun aiRun(ms: Long) = TimelineItem.AiRun(
        timestamp = Instant.fromEpochMilliseconds(ms),
        iteration = AiIterationUi(
            turnIndex = 0,
            timeLabel = "00:00",
            kind = RunKind.ScheduledBase,
            thread = AiThreadUi(turnIndex = 0, summary = null, process = emptyList(), response = null),
        ),
        sessionId = "s",
        isStreaming = false,
        isLatest = false,
    )

    @Test
    fun under_cap_is_untouched_and_not_truncated() {
        val result = capTimeline(events(10), cap = 24)
        assertEquals(10, result.items.size)
        assertFalse(result.truncated)
    }

    @Test
    fun exactly_at_cap_is_not_truncated() {
        val result = capTimeline(events(24), cap = 24)
        assertEquals(24, result.items.size)
        assertFalse(result.truncated)
    }

    @Test
    fun over_cap_keeps_the_leading_slice_and_flags_truncated() {
        val items = events(30)
        val result = capTimeline(items, cap = 24)
        assertEquals(24, result.items.size)
        assertTrue(result.truncated)
        assertEquals(items.take(24), result.items)
    }

    @Test
    fun headers_do_not_count_toward_the_cap() {
        val items = listOf(dayHeader(LocalDate(2026, 7, 1))) + events(24)
        val result = capTimeline(items, cap = 24)
        assertEquals(24, result.items.count { it !is TimelineItem.DayHeader })
        assertFalse(result.truncated)
    }

    @Test
    fun a_trailing_dangling_header_is_dropped() {
        val items = events(5) + dayHeader(LocalDate(2026, 7, 1))
        val result = capTimeline(items, cap = 24)
        assertEquals(5, result.items.size)
        assertTrue(result.items.none { it is TimelineItem.DayHeader })
    }

    /** DayHeader is purely a sticky decoration — it's still inserted structurally into the list at
     *  every local-day boundary so `stickyHeader` has something to pin, but it must stay invisible
     *  to segment/cap/asleep-state derivation (see the timelineAsleepStates tests below and
     *  SessionTimeline.kt's firstAnchorIndex/lastAnchorIndex). Today's own header is skipped
     *  entirely — the user already knows it's today. */
    @Test
    fun buildTimeline_inserts_a_header_at_every_local_day_boundary_except_today() {
        val tz = TimeZone.currentSystemDefault()
        val today = Clock.System.now().toLocalDateTime(tz).date
        val todayEvent = SessionEvent(
            timestamp = today.atStartOfDayIn(tz) + 12.hours,
            trigger = TriggerType.AlarmDismissed,
            data = EventData.Empty,
        )
        val yesterdayEvent = SessionEvent(
            timestamp = today.atStartOfDayIn(tz) - 12.hours,
            trigger = TriggerType.AlarmDismissed,
            data = EventData.Empty,
        )
        val session = TimelineSession(
            sessionId = "s1",
            iterations = emptyList(),
            events = listOf(todayEvent, yesterdayEvent),
            streamingTurnIndex = null,
        )
        val timeline = buildTimeline(listOf(session))
        assertEquals(3, timeline.size)
        assertTrue(timeline[0] is TimelineItem.Event)
        assertTrue(timeline[1] is TimelineItem.DayHeader)
        assertTrue(timeline[2] is TimelineItem.Event)
    }

    /** Regression: a data-layer race can surface the identical (trigger, timestamp) event under two
     *  different sessions; the LazyColumn key must stay unique regardless of how that happens. */
    @Test
    fun buildTimeline_collapses_the_same_event_appearing_in_two_sessions() {
        val duplicate = SessionEvent(
            timestamp = Instant.fromEpochMilliseconds(1_783_065_605_883L),
            trigger = TriggerType.AlarmDismissed,
            data = EventData.Empty,
        )
        val sessionA = TimelineSession(
            sessionId = "s1",
            iterations = emptyList(),
            events = listOf(duplicate),
            streamingTurnIndex = null,
        )
        val sessionB = TimelineSession(
            sessionId = "s2",
            iterations = emptyList(),
            events = listOf(duplicate),
            streamingTurnIndex = null,
        )
        val timeline = buildTimeline(listOf(sessionA, sessionB))
        assertEquals(1, timeline.count { it is TimelineItem.Event })
        assertEquals(timeline.map { it.id }.toSet().size, timeline.size)
    }

    @Test
    fun timelineAsleepStates_with_no_sleep_events_is_all_awake() {
        val timeline = listOf(event(30, TriggerType.CalendarChange), event(20, TriggerType.AlarmDismissed), aiRun(10))
        assertEquals(listOf(false, false, false), timelineAsleepStates(timeline))
    }

    @Test
    fun timelineAsleepStates_marks_the_span_between_onset_and_wake_asleep() {
        // Reverse-chronological: index 0 newest. Sandwiched item at 20 sits between wake (30) and onset (10).
        val timeline = listOf(
            event(40, TriggerType.CalendarChange), // after wake -> awake
            event(30, TriggerType.OutOfBedConfirmed), // the wake row itself -> awake
            aiRun(20), // sandwiched between onset and wake -> asleep
            event(10, TriggerType.SleepOnset), // the onset row itself -> asleep
            event(0, TriggerType.CalendarChange), // before onset -> awake
        )
        assertEquals(listOf(false, false, true, true, false), timelineAsleepStates(timeline))
    }

    @Test
    fun timelineAsleepStates_alternates_across_multiple_onset_wake_pairs() {
        val timeline = listOf(
            event(70, TriggerType.CalendarChange), // after 2nd wake -> awake
            event(60, TriggerType.OutOfBedConfirmed), // 2nd wake -> awake
            event(50, TriggerType.SleepOnset), // 2nd nap -> asleep
            event(40, TriggerType.OutOfBedConfirmed), // 1st wake -> awake
            event(30, TriggerType.SleepOnset), // 1st nap -> asleep
        )
        assertEquals(listOf(false, false, true, false, true), timelineAsleepStates(timeline))
    }

    /** A day boundary must never reset the asleep flag — most sleep sessions span midnight, and
     *  DayHeader is purely visual, a no-op for this derivation. */
    @Test
    fun timelineAsleepStates_stays_asleep_across_a_day_header_even_mid_sleep() {
        val timeline = listOf(
            event(20, TriggerType.CalendarChange), // new day, still asleep -> asleep
            dayHeader(LocalDate(2026, 7, 2)),
            event(10, TriggerType.SleepOnset), // asleep at the end of the prior day
        )
        assertEquals(listOf(true, true, true), timelineAsleepStates(timeline))
    }

    /** True cold start: no reference point yet, so the first load must render fully static, never
     *  an animated reveal. */
    @Test
    fun diffNewlyArrivedIds_the_very_first_check_never_marks_anything_new() {
        assertEquals(emptySet<String>(), diffNewlyArrivedIds(setOf("a", "b"), previousIds = null))
    }

    @Test
    fun diffNewlyArrivedIds_a_later_check_marks_only_the_added_id() {
        assertEquals(setOf("c"), diffNewlyArrivedIds(setOf("a", "b", "c"), previousIds = setOf("a", "b")))
    }

    @Test
    fun diffNewlyArrivedIds_an_identical_re_check_marks_nothing_new() {
        // The Home→Settings→back case: same ids as last time, nothing should animate.
        assertEquals(emptySet<String>(), diffNewlyArrivedIds(setOf("a", "b"), previousIds = setOf("a", "b")))
    }

    @Test
    fun diffNewlyArrivedIds_a_removed_id_does_not_appear_as_new() {
        assertEquals(emptySet<String>(), diffNewlyArrivedIds(setOf("a"), previousIds = setOf("a", "b")))
    }

    /** Guards the entrance-animation pipeline: newlyArrivedIds is only meaningful if buildTimeline
     *  itself doesn't spuriously reshuffle/rename ids across repeated calls with the same underlying
     *  session data. */
    @Test
    fun buildTimeline_is_idempotent_given_identical_input() {
        val session = TimelineSession(
            sessionId = "s1",
            iterations = listOf(
                AiIterationUi(
                    turnIndex = 0,
                    timeLabel = "07:45",
                    kind = RunKind.ScheduledBase,
                    thread = AiThreadUi(turnIndex = 0, summary = null, process = emptyList(), response = null),
                    ranAtEpochMs = 1_000L,
                ),
            ),
            events = listOf(
                SessionEvent(timestamp = Instant.fromEpochMilliseconds(500L), trigger = TriggerType.AlarmDismissed, data = EventData.Empty),
            ),
            streamingTurnIndex = null,
        )
        val first = buildTimeline(listOf(session))
        val second = buildTimeline(listOf(session))
        assertEquals(first, second)
        assertEquals(first.map { it.id }, second.map { it.id })
    }
}
