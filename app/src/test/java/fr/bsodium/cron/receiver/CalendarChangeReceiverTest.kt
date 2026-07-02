package fr.bsodium.cron.receiver

import android.app.Application
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import fr.bsodium.cron.worker.CalendarChangeWorker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CalendarChangeReceiverTest {

    private lateinit var app: Application
    private lateinit var workManager: WorkManager

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(app)
        workManager = WorkManager.getInstance(app)
    }

    @Test
    fun onReceive_enqueues_unique_calendar_change_work() {
        CalendarChangeReceiver().onReceive(app, Intent())

        val infos = workManager.getWorkInfosForUniqueWork(CalendarChangeWorker.NAME).get()
        assertEquals(1, infos.size)
        assertTrue(infos.single().state == WorkInfo.State.ENQUEUED)
    }

    @Test
    fun onReceive_twice_in_a_row_replaces_rather_than_stacking() {
        CalendarChangeReceiver().onReceive(app, Intent())
        CalendarChangeReceiver().onReceive(app, Intent())

        val infos = workManager.getWorkInfosForUniqueWork(CalendarChangeWorker.NAME).get()
        // REPLACE cancels the first request and enqueues a fresh one — only one survives as non-cancelled.
        assertEquals(1, infos.count { it.state != WorkInfo.State.CANCELLED })
    }
}
