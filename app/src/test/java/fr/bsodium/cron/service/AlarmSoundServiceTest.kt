package fr.bsodium.cron.service

import android.app.Application
import android.app.NotificationManager
import android.app.Service.START_NOT_STICKY
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import fr.bsodium.cron.receiver.AlarmReceiver
import fr.bsodium.cron.ui.screens.alarm.AlarmActivity
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

    @Test
    fun start_launches_alarm_activity_directly() {
        controller = Robolectric.buildService(
            AlarmSoundService::class.java,
            Intent(app, AlarmSoundService::class.java).apply {
                putExtra(AlarmReceiver.EXTRA_LABEL, "Wake up")
                putExtra(AlarmReceiver.EXTRA_REQUEST_CODE, 42)
            },
        )
        controller?.create()?.startCommand(0, 0)

        val started = shadowOf(app).nextStartedActivity
        assertEquals(AlarmActivity::class.java.name, started?.component?.className)
        assertEquals(42, started?.getIntExtra(AlarmReceiver.EXTRA_REQUEST_CODE, -1))
    }

    @Test
    fun two_overlapping_alarm_fires_do_not_crash_or_duplicate_the_activity_launch() {
        controller = Robolectric.buildService(
            AlarmSoundService::class.java,
            Intent(app, AlarmSoundService::class.java).apply {
                putExtra(AlarmReceiver.EXTRA_LABEL, "Wake up")
                putExtra(AlarmReceiver.EXTRA_REQUEST_CODE, 1)
            },
        )
        val service = requireNotNull(controller?.create()?.startCommand(0, 0)?.get())
        shadowOf(app).nextStartedActivity // drain the first launch

        service.onStartCommand(
            Intent(app, AlarmSoundService::class.java).apply {
                putExtra(AlarmReceiver.EXTRA_LABEL, "Wake up (hard latest)")
                putExtra(AlarmReceiver.EXTRA_REQUEST_CODE, 2)
            },
            0,
            1,
        )

        val secondLaunch = shadowOf(app).nextStartedActivity
        assertEquals(2, secondLaunch?.getIntExtra(AlarmReceiver.EXTRA_REQUEST_CODE, -1))
        assertEquals(1, activeNotificationIds().count { it == AlarmReceiver.NOTIFICATION_ID })
    }

    @Test
    fun activity_shown_for_the_current_ring_drops_the_full_screen_intent_fallback() {
        controller = Robolectric.buildService(
            AlarmSoundService::class.java,
            Intent(app, AlarmSoundService::class.java).apply {
                putExtra(AlarmReceiver.EXTRA_LABEL, "Wake up")
                putExtra(AlarmReceiver.EXTRA_REQUEST_CODE, 7)
            },
        )
        val service = requireNotNull(controller?.create()?.startCommand(0, 0)?.get())
        assertTrue(notificationFullScreenIntentPresent())

        service.onStartCommand(AlarmSoundService.activityShownIntent(app, 7), 0, 1)

        assertTrue(activeNotificationIds().contains(AlarmReceiver.NOTIFICATION_ID)) // ring stays up
        assertTrue(!notificationFullScreenIntentPresent())
    }

    @Test
    fun activity_shown_for_a_superseded_ring_is_ignored() {
        controller = Robolectric.buildService(
            AlarmSoundService::class.java,
            Intent(app, AlarmSoundService::class.java).apply {
                putExtra(AlarmReceiver.EXTRA_LABEL, "Wake up")
                putExtra(AlarmReceiver.EXTRA_REQUEST_CODE, 1)
            },
        )
        val service = requireNotNull(controller?.create()?.startCommand(0, 0)?.get())
        // A second, real ring supersedes the first (matches the live AI-alarm-then-hard-latest case).
        service.onStartCommand(
            Intent(app, AlarmSoundService::class.java).apply {
                putExtra(AlarmReceiver.EXTRA_LABEL, "Wake up (hard latest)")
                putExtra(AlarmReceiver.EXTRA_REQUEST_CODE, 2)
            },
            0,
            1,
        )
        assertTrue(notificationFullScreenIntentPresent())

        // A stale "shown" signal from the superseded first ring must not touch the live one's notification.
        service.onStartCommand(AlarmSoundService.activityShownIntent(app, 1), 0, 2)

        assertTrue(notificationFullScreenIntentPresent())
    }

    private fun activeNotificationIds(): List<Int> =
        shadowOf(notificationManager).activeNotifications.map { it.id }

    private fun notificationFullScreenIntentPresent(): Boolean =
        shadowOf(notificationManager).activeNotifications
            .first { it.id == AlarmReceiver.NOTIFICATION_ID }
            .notification.fullScreenIntent != null
}
