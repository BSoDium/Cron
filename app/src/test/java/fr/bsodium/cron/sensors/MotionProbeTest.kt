package fr.bsodium.cron.sensors

import org.junit.Assert.assertEquals
import org.junit.Test

class MotionProbeTest {

    @Test
    fun no_samples_is_unknown() {
        assertEquals(MotionClassification.Unknown, MotionProbe.classify(sampleCount = 0, peakDeltaG = 0f, variance = 0f))
    }

    @Test
    fun low_peak_is_still() {
        assertEquals(MotionClassification.Still, MotionProbe.classify(sampleCount = 50, peakDeltaG = 0.3f, variance = 0.05f))
    }

    @Test
    fun a_single_jostle_is_handled_not_walking() {
        // High peak (the pickup itself) but low variance -- one spike, not a sustained rhythm.
        assertEquals(MotionClassification.Handled, MotionProbe.classify(sampleCount = 50, peakDeltaG = 2.0f, variance = 0.4f))
    }

    @Test
    fun sustained_high_variance_and_peak_is_walking() {
        assertEquals(MotionClassification.Walking, MotionProbe.classify(sampleCount = 50, peakDeltaG = 4.0f, variance = 3.0f))
    }

    @Test
    fun high_variance_alone_without_a_walking_peak_is_handled() {
        assertEquals(MotionClassification.Handled, MotionProbe.classify(sampleCount = 50, peakDeltaG = 1.8f, variance = 5.0f))
    }
}
