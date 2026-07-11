package fr.bsodium.cron.worker

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import androidx.work.workDataOf
import fr.bsodium.cron.session.db.CronDatabase
import fr.bsodium.cron.session.db.toEntity
import fr.bsodium.cron.session.model.EventData
import fr.bsodium.cron.session.model.SessionEvent
import fr.bsodium.cron.session.model.TriggerType
import fr.bsodium.cron.settings.SettingsRepository
import fr.bsodium.cron.testutil.Fixtures
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SleepSessionWriteWorkerTest {

    private lateinit var app: Application
    private lateinit var db: CronDatabase

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(app)
        db = CronDatabase.get(app)
        // The production CronDatabase singleton is file-backed and persists across tests in the JVM; wipe it (cascades to events) so each test starts from a clean slate.
        runBlocking { db.sessionDao().deleteOlderThan(Long.MAX_VALUE) }
    }

    @Test
    fun missing_session_id_fails_without_writing() = runTest {
        val worker = TestListenableWorkerBuilder.from(app, SleepSessionWriteWorker::class.java).build()

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.failure(), result)
    }

    @Test
    fun session_not_found_skips_the_write() = runTest {
        val result = buildWorker("nope").doWork()

        assertEquals(ListenableWorker.Result.success(), result)
    }

    @Test
    fun toggle_off_skips_the_write() = runTest {
        insertSession("s1", withSleepWindow = true)
        SettingsRepository(app).setSaveSleepToHealthConnect(false)

        val result = buildWorker("s1").doWork()

        assertEquals(ListenableWorker.Result.success(), result)
    }

    @Test
    fun no_onset_wake_pair_skips_the_write() = runTest {
        insertSession("s1", withSleepWindow = false)

        val result = buildWorker("s1").doWork()

        assertEquals(ListenableWorker.Result.success(), result)
    }

    @Test
    fun missing_hc_permission_skips_the_write() = runTest {
        // Health Connect is unavailable under Robolectric, so hasWritePermission() deterministically returns false here without needing to mock it.
        insertSession("s1", withSleepWindow = true)

        val result = buildWorker("s1").doWork()

        assertEquals(ListenableWorker.Result.success(), result)
    }

    private suspend fun insertSession(sessionId: String, withSleepWindow: Boolean) {
        db.sessionDao().insert(Fixtures.session(id = sessionId).toEntity())
        if (withSleepWindow) {
            val onset = SessionEvent(TriggerType.SleepOnset, Fixtures.at("2026-05-22T22:00:00Z"), EventData.Empty)
            val wake = SessionEvent(TriggerType.OutOfBedConfirmed, Fixtures.at("2026-05-23T06:00:00Z"), EventData.Empty)
            db.eventDao().insert(onset.toEntity(sessionId))
            db.eventDao().insert(wake.toEntity(sessionId))
        }
    }

    private fun buildWorker(sessionId: String): SleepSessionWriteWorker =
        TestListenableWorkerBuilder.from(app, SleepSessionWriteWorker::class.java)
            .setInputData(workDataOf(SleepSessionWriteWorker.KEY_SESSION_ID to sessionId))
            .build()
}
