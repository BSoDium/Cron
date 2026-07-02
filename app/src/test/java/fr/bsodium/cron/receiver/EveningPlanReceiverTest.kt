package fr.bsodium.cron.receiver

import android.app.Application
import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import fr.bsodium.cron.alarm.AlarmConstants
import fr.bsodium.cron.settings.SettingsRepository
import fr.bsodium.cron.testutil.awaitCondition
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
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
        // Room's CronDatabase singleton and DataStore's backing file aren't reset between test
        // classes by Robolectric, so a disabled toggle can leak in from an unrelated test — reset it.
        runBlocking { SettingsRepository(app).setAutoAlarmsEnabled(true) }
    }

    private fun dispatch() {
        val receiver = EveningPlanReceiver()
        app.registerReceiver(receiver, IntentFilter(EveningPlanReceiver.ACTION_FIRE))
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
        val started = shadowOf(app).nextStartedService
        assertNotNull(started)
    }

    @Test
    fun auto_alarms_disabled_skips_planning_and_does_not_rearm() = runBlocking {
        SettingsRepository(app).setAutoAlarmsEnabled(false)

        dispatch()
        shadowOf(Looper.getMainLooper()).idle()

        // Give the background coroutine a moment; then assert neither side effect happened.
        Thread.sleep(200)
        assertNull(nextTriggerPendingIntent())
        assertNull(shadowOf(app).nextStartedService)
    }
}
