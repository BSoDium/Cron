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
        weekdayLabel = date.toString(),
        dateLabel = date.toString(),
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

    @Test
    fun buildTimeline_inserts_a_header_at_every_local_day_boundary() {
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
        assertEquals(4, timeline.size)
        assertTrue(timeline[0] is TimelineItem.DayHeader)
        assertTrue(timeline[1] is TimelineItem.Event)
        assertTrue(timeline[2] is TimelineItem.DayHeader)
        assertTrue(timeline[3] is TimelineItem.Event)
    }

    @Test
    fun buildTimeline_collapses_the_same_event_appearing_in_two_sessions() {
        // Regression: a data-layer race can surface the identical (trigger, timestamp) event under two
        // different sessions; the LazyColumn key must stay unique regardless of how that happens.
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
}
