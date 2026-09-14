package fr.bsodium.cron.service

import android.app.Application
import android.app.NotificationManager
import android.app.Service.START_NOT_STICKY
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import fr.bsodium.cron.receiver.AlarmReceiver
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController

@RunWith(RobolectricTestRunner::class)
class AlarmSoundServiceTest {

    private lateinit var app: Application
    private lateinit var notificationManager: NotificationManager
    private var controller: ServiceController<AlarmSoundService>? = null

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        notificationManager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    @After
    fun tearDown() {
        controller?.destroy()
    }

    @Test
    fun start_posts_the_ringing_notification_under_the_shared_alarm_id() {
        controller = Robolectric.buildService(
            AlarmSoundService::class.java,
            Intent(app, AlarmSoundService::class.java).apply {
                putExtra(AlarmReceiver.EXTRA_LABEL, "Wake up")
            },
        )
        controller?.create()?.startCommand(0, 0)

        assertTrue(activeNotificationIds().contains(AlarmReceiver.NOTIFICATION_ID))
    }

    @Test
    fun action_stop_removes_the_notification() {
        controller = Robolectric.buildService(
            AlarmSoundService::class.java,
            Intent(app, AlarmSoundService::class.java).apply {
                putExtra(AlarmReceiver.EXTRA_LABEL, "Wake up")
            },
        )
        controller?.create()?.startCommand(0, 0)
        assertTrue(activeNotificationIds().contains(AlarmReceiver.NOTIFICATION_ID))

        val service = requireNotNull(controller?.get())
        val result = service.onStartCommand(AlarmSoundService.stopIntent(app), 0, 1)

        assertEquals(START_NOT_STICKY, result)
        assertTrue(activeNotificationIds().none { it == AlarmReceiver.NOTIFICATION_ID })
    }

    private fun activeNotificationIds(): List<Int> =
        shadowOf(notificationManager).activeNotifications.map { it.id }
}
