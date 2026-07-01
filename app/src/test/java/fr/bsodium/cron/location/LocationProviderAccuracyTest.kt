package fr.bsodium.cron.location

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class LocationProviderAccuracyTest {

    @Test
    fun fix_at_exactly_the_cap_is_within_bounds() {
        assertTrue(LocationProvider.isWithinAccuracyCap(500f, maxAccuracyMeters = 500f))
    }

    @Test
    fun fix_coarser_than_the_cap_is_rejected() {
        assertFalse(LocationProvider.isWithinAccuracyCap(500.1f, maxAccuracyMeters = 500f))
    }

    @Test
    fun fix_finer_than_the_cap_is_accepted() {
        assertTrue(LocationProvider.isWithinAccuracyCap(10f, maxAccuracyMeters = 500f))
    }

    @Test
    fun coarse_cap_accepts_city_level_accuracy_that_the_precise_cap_would_reject() {
        assertFalse(LocationProvider.isWithinAccuracyCap(4000f, maxAccuracyMeters = 500f))
        assertTrue(LocationProvider.isWithinAccuracyCap(4000f, maxAccuracyMeters = 5000f))
    }

    @Test
    fun last_known_fix_exactly_at_max_age_counts_as_recent() {
        val now = 10.hours.inWholeMilliseconds
        val fixTime = now - 2.hours.inWholeMilliseconds
        assertTrue(LocationProvider.isRecent(now, fixTime, maxAge = 2.hours))
    }

    @Test
    fun last_known_fix_older_than_max_age_is_stale() {
        val now = 10.hours.inWholeMilliseconds
        val fixTime = now - 2.hours.inWholeMilliseconds - 1.minutes.inWholeMilliseconds
        assertFalse(LocationProvider.isRecent(now, fixTime, maxAge = 2.hours))
    }

    @Test
    fun fresh_fix_is_recent() {
        val now = 10.hours.inWholeMilliseconds
        assertTrue(LocationProvider.isRecent(now, now, maxAge = 2.hours))
    }
}
