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
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.seconds

/**
 * Regresses the false-wake bug: a momentary phone pickup while still asleep must not end sleep
 * tracking. [ScreenStateMonitor.onUserPresent] only confirms out-of-bed after the unlock has stayed
 * sustained for [outOfBedThreshold] — a re-lock before then must abort it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ScreenStateMonitorOutOfBedDebounceTest {

    private val app: Application = ApplicationProvider.getApplicationContext()
    private val threshold = 90.seconds

    private class RecordingSink : SensorEventSink {
        val received = mutableListOf<SessionEvent>()
        override suspend fun emit(event: SessionEvent) {
            received += event
        }
    }

    private fun powerManager() = app.getSystemService(Context.POWER_SERVICE) as PowerManager

    private fun sendBroadcastAndIdle(action: String) {
        app.sendBroadcast(Intent(action))
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun a_momentary_glance_that_relocks_before_threshold_does_not_end_sleep_tracking() = runTest {
        shadowOf(powerManager()).setIsInteractive(false)
        val sink = RecordingSink()
        val monitor = ScreenStateMonitor(
            context = app,
            sink = sink,
            scope = this,
            sleepOnsetThreshold = ZERO,
            outOfBedThreshold = threshold,
            lightReader = AmbientLightReader(app),
            isAlarmRinging = { false },
        )
        try {
            monitor.start()
            advanceUntilIdle()
            sink.received.clear()

            // User picks up the phone briefly, still in bed.
            shadowOf(powerManager()).setIsInteractive(true)
            sendBroadcastAndIdle(Intent.ACTION_USER_PRESENT)
            advanceTimeBy(30.seconds)

            // ...then puts it back down and falls back asleep, well before the threshold.
            shadowOf(powerManager()).setIsInteractive(false)
            sendBroadcastAndIdle(Intent.ACTION_SCREEN_OFF)
            advanceUntilIdle()

            assertEquals(
                "a glance that re-locks before the threshold must not confirm out-of-bed",
                0,
                sink.received.count { it.trigger == TriggerType.OutOfBedConfirmed },
            )
        } finally {
            monitor.stop()
        }
    }

    @Test
    fun a_sustained_unlock_past_threshold_confirms_out_of_bed() = runTest {
        shadowOf(powerManager()).setIsInteractive(false)
        val sink = RecordingSink()
        val monitor = ScreenStateMonitor(
            context = app,
            sink = sink,
            scope = this,
            sleepOnsetThreshold = ZERO,
            outOfBedThreshold = threshold,
            lightReader = AmbientLightReader(app),
            isAlarmRinging = { false },
        )
        try {
            monitor.start()
            advanceUntilIdle()
            sink.received.clear()

            shadowOf(powerManager()).setIsInteractive(true)
            sendBroadcastAndIdle(Intent.ACTION_USER_PRESENT)
            advanceUntilIdle()

            assertEquals(1, sink.received.count { it.trigger == TriggerType.OutOfBedConfirmed })
        } finally {
            monitor.stop()
        }
    }
}
