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
}
