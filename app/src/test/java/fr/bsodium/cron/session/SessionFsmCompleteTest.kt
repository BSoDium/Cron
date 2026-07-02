package fr.bsodium.cron.session

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import fr.bsodium.cron.session.model.EventData
import fr.bsodium.cron.session.model.SessionEvent
import fr.bsodium.cron.session.model.SessionStatus
import fr.bsodium.cron.session.model.TriggerType
import fr.bsodium.cron.testutil.Fixtures
import fr.bsodium.cron.worker.AiTurnWorker
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Regresses Bug 2: completing a session (e.g. AlarmDismissed while already Awake) must cancel any
 * in-flight AI turn — otherwise a worker enqueued by the just-prior OutOfBedConfirmed event can still
 * run against the completed session and arm a ghost alarm nothing in the lifecycle will clear.
 */
@RunWith(RobolectricTestRunner::class)
class SessionFsmCompleteTest {

    private lateinit var app: Application
    private lateinit var repository: SessionRepository
    private lateinit var fsm: SessionFsm

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(app)
        repository = SessionRepository(app)
        fsm = SessionFsm(app, repository)
        runBlocking { repository.clearAll() }
    }

    @Test
    fun completing_a_session_cancels_its_in_flight_ai_turn() = runBlocking {
        val plan = Fixtures.dayPlan()
        val session = repository.createSession(plan, Fixtures.DATE, "Europe/Paris")
        repository.updateStatus(session.id, SessionStatus.Awake)
        repository.triggerAiTurn(session.id)

        val workName = "${AiTurnWorker.WORK_PREFIX}${session.id}"
        val beforeInfos = WorkManager.getInstance(app).getWorkInfosForUniqueWork(workName).get()
        assertTrue(
            "precondition: AI turn should be enqueued before completion",
            beforeInfos.any { it.state == WorkInfo.State.ENQUEUED },
        )

        fsm.onEvent(
            SessionEvent(
                trigger = TriggerType.AlarmDismissed,
                timestamp = Fixtures.T0,
                data = EventData.Empty,
            ),
        )

        val completed = repository.findById(session.id)
        assertTrue(completed?.status == SessionStatus.Complete)

        val afterInfos = WorkManager.getInstance(app).getWorkInfosForUniqueWork(workName).get()
        assertTrue(
            "AI turn must be cancelled once the session completes",
            afterInfos.all { it.state.isFinished && it.state == WorkInfo.State.CANCELLED },
        )
    }
}
