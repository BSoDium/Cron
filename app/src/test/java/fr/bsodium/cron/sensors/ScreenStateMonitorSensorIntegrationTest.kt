package fr.bsodium.cron.sensors

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Looper
import android.os.PowerManager
import androidx.test.core.app.ApplicationProvider
import fr.bsodium.cron.session.model.EventData
import fr.bsodium.cron.session.model.Placement
import fr.bsodium.cron.session.model.SessionEvent
import fr.bsodium.cron.session.model.TriggerType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

/**
 * Drives the full [ScreenStateMonitor] state machine (onset, motion-confirmed wake, Enclosed
 * placement) with fake [ProximitySource]/[MotionSource] instead of real sensors, so the four manual
 * on-device scenarios from docs/sleep-detection-architecture.md §4 (and the code review it drove —
 * see #275) can be reproduced deterministically in the JVM: no cable, no room darkness, no physically
 * walking around. [ScreenStateMonitorOutOfBedDebounceTest] and [ScreenStateMonitorUserPresentTest]
 * cover the pre-existing sustained-unlock path this complements.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ScreenStateMonitorSensorIntegrationTest {

    private val app: Application = ApplicationProvider.getApplicationContext()
    private val powerManager get() = app.getSystemService(Context.POWER_SERVICE) as PowerManager

    private class RecordingSink : SensorEventSink {
        val received = mutableListOf<SessionEvent>()
        override suspend fun emit(event: SessionEvent) {
            received += event
        }
    }

    private class FakeProximitySource(private val covered: Boolean?) : ProximitySource {
        override suspend fun readCovered(timeout: kotlin.time.Duration): Boolean? = covered
    }

    private class FakeMotionSource(private val summary: MotionSummary) : MotionSource {
        override suspend fun sample(window: kotlin.time.Duration): MotionSummary = summary
    }

    private fun seedScreenOff() = shadowOf(powerManager).setIsInteractive(false)

    private fun sendBroadcastAndIdle(action: String) {
        app.sendBroadcast(Intent(action))
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun onsetOf(events: List<SessionEvent>) =
        events.single { it.trigger == TriggerType.SleepOnset }.data as EventData.SleepOnset

    /** A brief unlock-then-relock, cancelling any pending sustained-unlock confirm before it could
     *  fire — the trigger for [ScreenStateMonitor.checkMotionForWake]. */
    private fun briefUnlockThenRelock() {
        shadowOf(powerManager).setIsInteractive(true)
        sendBroadcastAndIdle(Intent.ACTION_USER_PRESENT)
        shadowOf(powerManager).setIsInteractive(false)
        sendBroadcastAndIdle(Intent.ACTION_SCREEN_OFF)
    }

    @Test
    fun walking_after_a_brief_unlock_confirms_out_of_bed_via_motion() = runTest {
        seedScreenOff()
        val sink = RecordingSink()
        val monitor = ScreenStateMonitor(
            context = app,
            sink = sink,
            scope = this,
            sleepOnsetThreshold = ZERO,
            outOfBedThreshold = 1.hours, // sustained-unlock path must not win this race
            isAlarmRinging = { false },
            motionProbe = FakeMotionSource(MotionSummary(50, 4.0f, 3.0f, MotionClassification.Walking)),
        )
        try {
            monitor.start()
            advanceUntilIdle()
            sink.received.clear()

            briefUnlockThenRelock()
            advanceUntilIdle()

            assertEquals(1, sink.received.count { it.trigger == TriggerType.OutOfBedConfirmed })
            val evidence = (sink.received.first { it.trigger == TriggerType.OutOfBedConfirmed }.data as EventData.OutOfBedConfirmed).evidence
            assertEquals(listOf("motion_walking"), evidence)
        } finally {
            monitor.stop()
        }
    }

    @Test
    fun setting_the_phone_down_still_after_a_brief_unlock_does_not_confirm_out_of_bed() = runTest {
        seedScreenOff()
        val sink = RecordingSink()
        val monitor = ScreenStateMonitor(
            context = app,
            sink = sink,
            scope = this,
            sleepOnsetThreshold = ZERO,
            outOfBedThreshold = 1.hours,
            isAlarmRinging = { false },
            motionProbe = FakeMotionSource(MotionSummary(50, 0.1f, 0.0001f, MotionClassification.Still)),
        )
        try {
            monitor.start()
            advanceUntilIdle()
            sink.received.clear()

            briefUnlockThenRelock()
            advanceUntilIdle()

            assertEquals(0, sink.received.count { it.trigger == TriggerType.OutOfBedConfirmed })
        } finally {
            monitor.stop()
        }
    }

    @Test
    fun a_single_handling_jostle_after_a_brief_unlock_does_not_confirm_out_of_bed() = runTest {
        // The exact "set it back down" case: a real, sizeable jostle from placement, but not
        // sustained/rhythmic enough to be Walking — must not be enough alone (regression for the
        // near-miss margin observed live: peak 16.9G, variance 1.54 against a 2.0 Walking threshold).
        seedScreenOff()
        val sink = RecordingSink()
        val monitor = ScreenStateMonitor(
            context = app,
            sink = sink,
            scope = this,
            sleepOnsetThreshold = ZERO,
            outOfBedThreshold = 1.hours,
            isAlarmRinging = { false },
            motionProbe = FakeMotionSource(MotionSummary(50, 16.9f, 1.54f, MotionClassification.Handled)),
        )
        try {
            monitor.start()
            advanceUntilIdle()
            sink.received.clear()

            briefUnlockThenRelock()
            advanceUntilIdle()

            assertEquals(0, sink.received.count { it.trigger == TriggerType.OutOfBedConfirmed })
        } finally {
            monitor.stop()
        }
    }

    @Test
    fun enclosed_placement_onsets_via_the_bedtime_window_not_the_dark_gate() = runTest {
        val sink = RecordingSink()
        val window = Clock.System.now() - 1.hours..Clock.System.now() + 1.hours
        val monitor = ScreenStateMonitor(
            context = app,
            sink = sink,
            scope = this,
            sleepOnsetThreshold = 10.seconds,
            onsetRecheckInterval = 1.seconds,
            proximityReader = FakeProximitySource(covered = true),
            bedtimeWindowProvider = { window },
        )
        try {
            monitor.start()
            advanceUntilIdle()
            sendBroadcastAndIdle(Intent.ACTION_SCREEN_OFF)
            // Enclosed requires 1.5x the base threshold (15s here), not just the base 10s.
            advanceTimeBy(16.seconds)
            advanceUntilIdle()

            val onset = sink.received.singleOrNull { it.trigger == TriggerType.SleepOnset }
            assertNotNull("expected an Enclosed-placement onset", onset)
            assertEquals(Placement.Enclosed, onsetOf(sink.received).placement)
        } finally {
            monitor.stop()
        }
    }

    @Test
    fun enclosed_placement_outside_the_bedtime_window_does_not_onset() = runTest {
        val sink = RecordingSink()
        val window = Clock.System.now() - 2.hours..Clock.System.now() - 1.hours // already elapsed
        val monitor = ScreenStateMonitor(
            context = app,
            sink = sink,
            scope = this,
            sleepOnsetThreshold = 5.seconds,
            onsetRecheckInterval = 1.seconds,
            proximityReader = FakeProximitySource(covered = true),
            bedtimeWindowProvider = { window },
        )
        try {
            monitor.start()
            advanceUntilIdle()
            sendBroadcastAndIdle(Intent.ACTION_SCREEN_OFF)
            // Bounded advance only -- the recheck loop legitimately never terminates while the window
            // never opens, so advanceUntilIdle() here would spin forever chasing "no more work."
            advanceTimeBy(30.seconds) // comfortably past 1.5x threshold

            assertEquals(0, sink.received.count { it.trigger == TriggerType.SleepOnset })
        } finally {
            monitor.stop()
        }
    }

    @Test
    fun already_asleep_a_still_relock_does_not_reonset_even_when_now_enclosed() = runTest {
        // Reproduces the on-device result that looked like a bug but wasn't: sleep already latched,
        // a brief unlock that reads Still on relock must not confirm wake AND must not spuriously
        // re-onset just because the phone happens to be enclosed when the recheck loop restarts.
        seedScreenOff()
        val sink = RecordingSink()
        val monitor = ScreenStateMonitor(
            context = app,
            sink = sink,
            scope = this,
            sleepOnsetThreshold = ZERO,
            outOfBedThreshold = 1.hours,
            isAlarmRinging = { false },
            proximityReader = FakeProximitySource(covered = true),
            motionProbe = FakeMotionSource(MotionSummary(10, 0.1f, 0.0001f, MotionClassification.Still)),
        )
        try {
            monitor.start()
            advanceUntilIdle()
            assertEquals(1, sink.received.count { it.trigger == TriggerType.SleepOnset })
            sink.received.clear()

            briefUnlockThenRelock()
            advanceUntilIdle()

            assertEquals(0, sink.received.count { it.trigger == TriggerType.OutOfBedConfirmed })
            assertEquals(0, sink.received.count { it.trigger == TriggerType.SleepOnset })
        } finally {
            monitor.stop()
        }
    }
}
