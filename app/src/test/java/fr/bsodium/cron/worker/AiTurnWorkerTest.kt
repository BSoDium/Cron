package fr.bsodium.cron.worker

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import androidx.work.workDataOf
import fr.bsodium.cron.session.db.CronDatabase
import fr.bsodium.cron.session.db.toEntity
import fr.bsodium.cron.session.model.SessionStatus
import fr.bsodium.cron.testutil.Fixtures
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AiTurnWorkerTest {

    private lateinit var app: Application
    private lateinit var db: CronDatabase

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(app)
        db = CronDatabase.get(app)
        // The production CronDatabase singleton is file-backed and persists across tests in the JVM;
        // wipe it (cascades to events + ai_messages) so each test starts from a clean slate.
        runBlocking { db.sessionDao().deleteOlderThan(Long.MAX_VALUE) }
    }

    @Test
    fun missing_session_fails_without_running_a_turn() = runTest {
        val result = buildWorker("nope").doWork()

        assertEquals(ListenableWorker.Result.failure(), result)
    }

    @Test
    fun completed_session_skips_the_turn_without_billing() = runTest {
        db.sessionDao().insert(Fixtures.session(id = "s1", status = SessionStatus.Complete).toEntity())

        val result = buildWorker("s1").doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        assertTrue(db.aiMessageDao().findBySession("s1").isEmpty())
    }

    private fun buildWorker(sessionId: String): AiTurnWorker =
        TestListenableWorkerBuilder.from(app, AiTurnWorker::class.java)
            .setInputData(workDataOf(AiTurnWorker.KEY_SESSION_ID to sessionId))
            .build()
}
