package fr.bsodium.cron.receiver

import android.app.Application
import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import fr.bsodium.cron.alarm.AlarmConstants
import fr.bsodium.cron.settings.SettingsRepository
import fr.bsodium.cron.testutil.awaitCondition
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class TimeZoneChangedReceiverTest {

    private lateinit var app: Application

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        // Reset persisted state because Robolectric shares it between test classes.
        runBlocking { SettingsRepository(app).setAutoAlarmsEnabled(true) }
    }

    @Test
    fun timezone_change_rearms_the_evening_plan_trigger() {
        val receiver = TimeZoneChangedReceiver()
        ContextCompat.registerReceiver(
            app, receiver, IntentFilter(Intent.ACTION_TIMEZONE_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        app.sendBroadcast(Intent(Intent.ACTION_TIMEZONE_CHANGED))
        shadowOf(Looper.getMainLooper()).idle()
        app.unregisterReceiver(receiver)

        awaitCondition {
            PendingIntent.getBroadcast(
                app,
                AlarmConstants.EVENING_PLAN_REQUEST_CODE,
                Intent(app, EveningPlanReceiver::class.java).apply { action = EveningPlanReceiver.ACTION_FIRE },
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            ) != null
        }
    }
}
