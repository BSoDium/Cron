package fr.bsodium.cron.service

import kotlinx.datetime.Instant
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.hours

/** Regression for #223: the pure staleness gate behind [SleepSessionService]'s location refresh. */
class SleepSessionServiceLocationStalenessTest {

    private val threshold = 4.hours
    private val capturedAt = Instant.parse("2026-05-22T20:00:00Z")

    @Test
    fun fresh_location_is_not_stale() {
        assertFalse(
            SleepSessionService.isLocationStale(capturedAt, capturedAt + threshold - 1.hours, threshold),
        )
    }

    @Test
    fun exactly_at_threshold_is_stale() {
        assertTrue(
            SleepSessionService.isLocationStale(capturedAt, capturedAt + threshold, threshold),
        )
    }

    @Test
    fun well_past_threshold_is_stale() {
        assertTrue(
            SleepSessionService.isLocationStale(capturedAt, capturedAt + threshold * 3, threshold),
        )
    }
}
