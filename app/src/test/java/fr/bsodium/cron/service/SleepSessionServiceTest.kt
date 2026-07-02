package fr.bsodium.cron.service

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.WorkManagerTestInitHelper
import fr.bsodium.cron.session.db.CronDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController

@RunWith(RobolectricTestRunner::class)
class SleepSessionServiceTest {

    private lateinit var app: Application
    private lateinit var notificationManager: NotificationManager
    private var controller: ServiceController<SleepSessionService>? = null

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(app)
        runBlocking { CronDatabase.get(app).sessionDao().deleteOlderThan(Long.MAX_VALUE) }
        notificationManager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    @After
    fun tearDown() {
        controller?.destroy()
    }

    /**
     * Regresses Bug 1: a REARM delivered to a service instance that was just (re)created by Android
     * (e.g. after the OS killed the previous instance) must NOT silently no-op on a null monitor — it
     * must go through the same construction path as a normal start, ending up foregrounded with a
     * running notification, exactly as ACTION_STOP/no-action starts do today.
     */
    @Test
    fun rearm_on_freshly_created_service_starts_foreground_and_builds_monitor() {
        controller = Robolectric.buildService(
            SleepSessionService::class.java,
            SleepSessionService.rearmIntent(app),
        )
        controller?.create()?.startCommand(0, 0)

        val notification = shadowOf(notificationManager).activeNotifications
            .firstOrNull { it.id == SleepSessionService.NOTIFICATION_ID }
        assertTrue(
            "REARM on a fresh service instance must still post the foreground notification " +
                "(i.e. construct monitors), not silently no-op on a null screenStateMonitor",
            notification != null,
        )
    }

    @Test
    fun plain_start_then_rearm_keeps_service_foregrounded() {
        controller = Robolectric.buildService(SleepSessionService::class.java, SleepSessionService.startIntent(app))
        val service = controller?.create()?.startCommand(0, 0)?.get()
        requireNotNull(service)

        service.onStartCommand(SleepSessionService.rearmIntent(app), 0, 1)

        val notification = shadowOf(notificationManager).activeNotifications
            .firstOrNull { it.id == SleepSessionService.NOTIFICATION_ID }
        assertTrue(notification != null)
    }
}
