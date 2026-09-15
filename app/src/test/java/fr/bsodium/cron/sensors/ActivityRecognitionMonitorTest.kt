package fr.bsodium.cron.sensors

import kotlinx.datetime.Instant
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.minutes

class ActivityRecognitionMonitorTest {

    private val threshold = 10.minutes
    private val since = Instant.parse("2026-05-22T02:00:00Z")

    @Test
    fun below_threshold_is_not_sustained() {
        assertFalse(
            ActivityRecognitionMonitor.isSustainedMovement(since, since + threshold - 1.minutes, threshold),
        )
    }

    @Test
    fun exactly_at_threshold_is_sustained() {
        assertTrue(
            ActivityRecognitionMonitor.isSustainedMovement(since, since + threshold, threshold),
        )
    }

    @Test
    fun past_threshold_is_sustained() {
        assertTrue(
            ActivityRecognitionMonitor.isSustainedMovement(since, since + threshold * 3, threshold),
        )
    }
}
