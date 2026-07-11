package fr.bsodium.cron.sensors

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Looper
import android.os.PowerManager
import androidx.test.core.app.ApplicationProvider
import fr.bsodium.cron.session.model.SessionEvent
import fr.bsodium.cron.session.model.TriggerType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import kotlin.time.Duration.Companion.ZERO

/**
 * Regresses Bug 3: [AlarmActivity][fr.bsodium.cron.ui.screens.alarm.AlarmActivity] dismisses the
 * keyguard on launch, so a real device fires USER_PRESENT a beat before the user's actual
 * slide-to-dismiss gesture. Left unguarded, that unlock alone drives the session to Awake and the
 * dismiss gesture right behind it then completes the session immediately — collapsing the
 * dismiss-while-asleep re-ring guarantee on the very first real dismissal.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ScreenStateMonitorUserPresentTest {

    private val app: Application = ApplicationProvider.getApplicationContext()

    private class RecordingSink : SensorEventSink {
        val received = mutableListOf<SessionEvent>()
        override suspend fun emit(event: SessionEvent) {
            received += event
        }
    }

    private fun seedScreenOff() {
        val powerManager = app.getSystemService(Context.POWER_SERVICE) as PowerManager
        shadowOf(powerManager).setIsInteractive(false)
    }

    private fun sendBroadcastAndIdle(action: String) {
        app.sendBroadcast(Intent(action))
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun user_present_while_alarm_ringing_does_not_emit_out_of_bed() = runTest {
        seedScreenOff()
        val sink = RecordingSink()
        val monitor = ScreenStateMonitor(
            context = app,
            sink = sink,
            scope = this,
            sleepOnsetThreshold = ZERO,
            lightReader = AmbientLightReader(app),
            isAlarmRinging = { true },
        )
        try {
            monitor.start()
            advanceUntilIdle()
            assertTrue(
                "precondition: onset should have latched before USER_PRESENT",
                sink.received.any { it.trigger == TriggerType.SleepOnset },
            )
            sink.received.clear()

            sendBroadcastAndIdle(Intent.ACTION_USER_PRESENT)
            advanceUntilIdle()

            assertTrue(
                "OutOfBedConfirmed must be suppressed while an alarm is ringing",
                sink.received.none { it.trigger == TriggerType.OutOfBedConfirmed },
            )
        } finally {
            monitor.stop()
        }
    }

    @Test
    fun user_present_while_no_alarm_ringing_emits_out_of_bed() = runTest {
        seedScreenOff()
        val sink = RecordingSink()
        val monitor = ScreenStateMonitor(
            context = app,
            sink = sink,
            scope = this,
            sleepOnsetThreshold = ZERO,
            outOfBedThreshold = ZERO,
            lightReader = AmbientLightReader(app),
            isAlarmRinging = { false },
        )
        try {
            monitor.start()
            advanceUntilIdle()
            sink.received.clear()

            // A real unlock leaves the device interactive — the sustained-unlock debounce itself is covered by ScreenStateMonitorOutOfBedDebounceTest.
            (app.getSystemService(Context.POWER_SERVICE) as PowerManager).let { shadowOf(it).setIsInteractive(true) }
            sendBroadcastAndIdle(Intent.ACTION_USER_PRESENT)
            advanceUntilIdle()

            assertEquals(1, sink.received.count { it.trigger == TriggerType.OutOfBedConfirmed })
        } finally {
            monitor.stop()
        }
    }
}
