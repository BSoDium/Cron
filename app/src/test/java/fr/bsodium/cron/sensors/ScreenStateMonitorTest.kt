package fr.bsodium.cron.sensors

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.minutes

class ScreenStateMonitorTest {

    private val base = 20.minutes

    @Test
    fun never_fires_in_a_lit_room() {
        assertFalse(
            ScreenStateMonitor.shouldEmitOnset(base * 5, base, isDark = false, isCharging = true),
        )
    }

    @Test
    fun charging_and_dark_fires_at_base_threshold() {
        assertTrue(
            ScreenStateMonitor.shouldEmitOnset(base, base, isDark = true, isCharging = true),
        )
    }

    @Test
    fun charging_and_dark_below_threshold_does_not_fire() {
        assertFalse(
            ScreenStateMonitor.shouldEmitOnset(base - 1.minutes, base, isDark = true, isCharging = true),
        )
    }

    @Test
    fun uncharged_needs_double_the_window() {
        // The face-down-on-a-table case: dark and idle, but not charging — must wait twice as long.
        assertFalse(
            ScreenStateMonitor.shouldEmitOnset(base, base, isDark = true, isCharging = false),
        )
        assertTrue(
            ScreenStateMonitor.shouldEmitOnset(base * 2, base, isDark = true, isCharging = false),
        )
    }
}
