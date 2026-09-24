package fr.bsodium.cron.service

import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.time.Duration.Companion.hours

/** The pure bedtime-window arithmetic behind [SleepSessionService.resolveBedtimeWindow]. */
class SleepSessionServiceBedtimeWindowTest {

    private val eveningPlanAt = Instant.parse("2026-05-22T20:00:00Z")
    private val hardLatestAt = Instant.parse("2026-05-23T09:30:00Z")
    private val margin = 4.hours

    @Test
    fun window_spans_evening_plan_through_hard_latest_minus_margin() {
        val window = requireNotNull(SleepSessionService.bedtimeWindowFrom(eveningPlanAt, hardLatestAt, margin))
        assertEquals(eveningPlanAt, window.start)
        assertEquals(hardLatestAt - margin, window.endInclusive)
    }

    @Test
    fun a_margin_that_collapses_the_window_is_null() {
        assertNull(SleepSessionService.bedtimeWindowFrom(eveningPlanAt, hardLatestAt, 14.hours))
    }
}
