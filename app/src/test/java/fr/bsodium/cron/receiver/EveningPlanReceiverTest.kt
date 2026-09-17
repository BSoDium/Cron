package fr.bsodium.cron.receiver

import android.app.Application
import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import fr.bsodium.cron.alarm.AlarmConstants
import fr.bsodium.cron.service.SleepSessionService
import fr.bsodium.cron.settings.SettingsRepository
import fr.bsodium.cron.testutil.awaitCondition
import fr.bsodium.cron.testutil.awaitNotNull
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class EveningPlanReceiverTest {

    private lateinit var app: Application

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        // Reset persisted state because Robolectric shares it between test classes.
        runBlocking { SettingsRepository(app).setAutoAlarmsEnabled(true) }
    }

    private fun dispatch() {
        val receiver = EveningPlanReceiver()
        ContextCompat.registerReceiver(
            app, receiver, IntentFilter(EveningPlanReceiver.ACTION_FIRE), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        app.sendBroadcast(Intent(EveningPlanReceiver.ACTION_FIRE))
        shadowOf(Looper.getMainLooper()).idle()
        app.unregisterReceiver(receiver)
    }

    private fun nextTriggerPendingIntent(): PendingIntent? =
        PendingIntent.getBroadcast(
            app,
            AlarmConstants.EVENING_PLAN_REQUEST_CODE,
            Intent(app, EveningPlanReceiver::class.java).apply { action = EveningPlanReceiver.ACTION_FIRE },
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )

    @Test
    fun fires_rearms_tomorrow_and_starts_the_sleep_session_service() {
        dispatch()

        awaitCondition { nextTriggerPendingIntent() != null }
        // Re-arming precedes service startup, so the alarm landing does not prove startup.
        val started = awaitNotNull { shadowOf(app).nextStartedService }
        assertEquals(SleepSessionService::class.java.name, started.component?.className)
    }

    @Test
    fun auto_alarms_disabled_skips_planning_and_does_not_rearm() = runBlocking {
        SettingsRepository(app).setAutoAlarmsEnabled(false)

        dispatch()
        shadowOf(Looper.getMainLooper()).idle()

        // Allow the background coroutine to settle before checking side effects.
        Thread.sleep(200)
        assertNull(nextTriggerPendingIntent())
        assertNull(shadowOf(app).nextStartedService)
    }
}
