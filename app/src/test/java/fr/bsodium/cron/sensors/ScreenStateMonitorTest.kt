package fr.bsodium.cron.sensors

import fr.bsodium.cron.testutil.Fixtures
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class ScreenStateMonitorTest {

    private val base = 20.minutes
    private val outOfBedThreshold = 90.seconds

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

    @Test
    fun sustained_interactive_unlock_confirms_out_of_bed() {
        assertTrue(
            ScreenStateMonitor.shouldConfirmOutOfBed(outOfBedThreshold, outOfBedThreshold, stillInteractive = true),
        )
    }

    @Test
    fun a_glance_below_threshold_does_not_confirm() {
        assertFalse(
            ScreenStateMonitor.shouldConfirmOutOfBed(outOfBedThreshold - 1.seconds, outOfBedThreshold, stillInteractive = true),
        )
    }

    @Test
    fun relocked_before_confirming_does_not_confirm_even_past_threshold() {
        assertFalse(
            ScreenStateMonitor.shouldConfirmOutOfBed(outOfBedThreshold * 2, outOfBedThreshold, stillInteractive = false),
        )
    }

    @Test
    fun walking_after_a_brief_unlock_confirms_out_of_bed() {
        assertTrue(ScreenStateMonitor.shouldConfirmWakeFromMotion(MotionClassification.Walking))
    }

    @Test
    fun a_single_handled_jostle_does_not_confirm_on_its_own() {
        // Setting the phone back down after the glance looks like this too -- not enough alone.
        assertFalse(ScreenStateMonitor.shouldConfirmWakeFromMotion(MotionClassification.Handled))
    }

    @Test
    fun still_or_unknown_motion_does_not_confirm() {
        assertFalse(ScreenStateMonitor.shouldConfirmWakeFromMotion(MotionClassification.Still))
        assertFalse(ScreenStateMonitor.shouldConfirmWakeFromMotion(MotionClassification.Unknown))
    }

    private val bedtimeWindow = Fixtures.at("2026-05-22T21:00:00Z")..Fixtures.at("2026-05-23T02:00:00Z")

    @Test
    fun enclosed_onset_fires_at_1_5x_threshold_inside_the_bedtime_window() {
        assertTrue(
            ScreenStateMonitor.shouldEmitEnclosedOnset(
                screenOff = base * 3,
                baseThreshold = base,
                now = Fixtures.at("2026-05-22T23:00:00Z"),
                bedtimeWindow = bedtimeWindow,
            ),
        )
    }

    @Test
    fun enclosed_onset_below_1_5x_threshold_does_not_fire() {
        assertFalse(
            ScreenStateMonitor.shouldEmitEnclosedOnset(
                screenOff = base,
                baseThreshold = base,
                now = Fixtures.at("2026-05-22T23:00:00Z"),
                bedtimeWindow = bedtimeWindow,
            ),
        )
    }

    @Test
    fun enclosed_onset_outside_the_bedtime_window_does_not_fire_even_past_threshold() {
        // A drawer at 3pm is dark too, but nowhere near this user's bedtime -- must not onset.
        assertFalse(
            ScreenStateMonitor.shouldEmitEnclosedOnset(
                screenOff = base * 10,
                baseThreshold = base,
                now = Fixtures.at("2026-05-22T15:00:00Z"),
                bedtimeWindow = bedtimeWindow,
            ),
        )
    }
}
