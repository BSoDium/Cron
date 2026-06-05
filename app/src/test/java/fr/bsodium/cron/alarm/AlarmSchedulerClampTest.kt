package fr.bsodium.cron.alarm

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class AlarmSchedulerClampTest {

    private val tz = TimeZone.of("Europe/Paris")
    private val date = LocalDate.parse("2026-05-22")
    private val hardLatest = LocalTime(10, 0) // 10:00 local
    private val hardLatestInstant = kotlinx.datetime.LocalDateTime(
        date.year, date.monthNumber, date.dayOfMonth,
        hardLatest.hour, hardLatest.minute, 0, 0,
    ).toInstant(tz)

    @Test
    fun reasonable_alarm_inside_window_is_not_clamped() {
        val now = Instant.parse("2026-05-22T03:00:00Z") // 05:00 Paris
        val requested = Instant.parse("2026-05-22T05:30:00Z") // 07:30 Paris
        val result = AlarmScheduler.clamp(requested, now, hardLatest, date, tz)
        assertEquals(requested, result.actualInstant)
        assertFalse(result.clampedToHardLatest)
    }

    @Test
    fun alarm_past_hard_latest_is_clamped_down() {
        val now = Instant.parse("2026-05-22T03:00:00Z")
        val tooLate = hardLatestInstant + 1.minutes
        val result = AlarmScheduler.clamp(tooLate, now, hardLatest, date, tz)
        assertEquals(hardLatestInstant, result.actualInstant)
        assertTrue(result.clampedToHardLatest)
    }

    @Test
    fun alarm_in_the_past_is_clamped_up_to_min_lead() {
        val now = Instant.parse("2026-05-22T05:00:00Z")
        val expectedLower = now + AlarmScheduler.MIN_LEAD
        val pastRequest = now - 5.minutes
        val result = AlarmScheduler.clamp(pastRequest, now, hardLatest, date, tz)
        assertEquals(expectedLower, result.actualInstant)
        // Lower-bound clamp is not a hard-latest clamp.
        assertFalse(result.clampedToHardLatest)
    }

    @Test
    fun exactly_hard_latest_is_not_flagged_as_clamped() {
        val now = Instant.parse("2026-05-22T03:00:00Z")
        val result = AlarmScheduler.clamp(hardLatestInstant, now, hardLatest, date, tz)
        assertEquals(hardLatestInstant, result.actualInstant)
        assertFalse(result.clampedToHardLatest)
    }

    @Test
    fun requested_exactly_at_min_lead_lower_bound_is_unchanged() {
        val now = Instant.parse("2026-05-22T03:00:00Z")
        val atLower = now + AlarmScheduler.MIN_LEAD
        val result = AlarmScheduler.clamp(atLower, now, hardLatest, date, tz)
        assertEquals(atLower, result.actualInstant)
        assertFalse(result.clampedToHardLatest)
    }

    @Test
    fun requested_equal_to_now_is_clamped_up_to_min_lead() {
        val now = Instant.parse("2026-05-22T03:00:00Z")
        val result = AlarmScheduler.clamp(now, now, hardLatest, date, tz)
        assertEquals(now + AlarmScheduler.MIN_LEAD, result.actualInstant)
        assertFalse(result.clampedToHardLatest)
    }

    @Test
    fun far_future_request_is_clamped_to_hard_latest() {
        val now = Instant.parse("2026-05-22T03:00:00Z")
        val result = AlarmScheduler.clamp(hardLatestInstant + 6.hours, now, hardLatest, date, tz)
        assertEquals(hardLatestInstant, result.actualInstant)
        assertTrue(result.clampedToHardLatest)
    }

    @Test
    fun request_on_the_wrong_day_is_pinned_to_the_session_morning() {
        // Model emitted the day AFTER the session morning. Only its time-of-day (07:30 Paris) is kept,
        // re-pinned onto sessionDate — so the alarm can never arm on the wrong day.
        val now = Instant.parse("2026-05-22T03:00:00Z") // 05:00 Paris, on sessionDate
        val nextDay = Instant.parse("2026-05-23T05:30:00Z") // 07:30 Paris, one day late
        val expected = Instant.parse("2026-05-22T05:30:00Z") // 07:30 Paris on sessionDate
        val result = AlarmScheduler.clamp(nextDay, now, hardLatest, date, tz)
        assertEquals(expected, result.actualInstant)
        assertFalse(result.clampedToHardLatest)
    }

    @Test
    fun now_already_past_hard_latest_falls_back_to_min_lead_without_throwing() {
        // Degenerate bound (lower > upper): now is past today's hard latest. Must not throw; slides to
        // now + MIN_LEAD.
        val now = Instant.parse("2026-05-22T09:00:00Z") // 11:00 Paris, past the 10:00 hard latest
        val requested = Instant.parse("2026-05-22T05:30:00Z") // 07:30 Paris
        val result = AlarmScheduler.clamp(requested, now, hardLatest, date, tz)
        assertEquals(now + AlarmScheduler.MIN_LEAD, result.actualInstant)
        assertFalse(result.clampedToHardLatest)
    }
}
