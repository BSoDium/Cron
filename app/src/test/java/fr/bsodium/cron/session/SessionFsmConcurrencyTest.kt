package fr.bsodium.cron.session

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.WorkManagerTestInitHelper
import fr.bsodium.cron.session.model.EventData
import fr.bsodium.cron.session.model.SessionEvent
import fr.bsodium.cron.session.model.SessionStatus
import fr.bsodium.cron.session.model.TriggerType
import fr.bsodium.cron.testutil.Fixtures
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.time.Duration.Companion.seconds

/**
 * Regression for #153: `onEvent` is invoked concurrently from independently-constructed [SessionFsm]
 * instances (one per call site -- service, receiver, worker, ViewModel), and each read-transition-write
 * body used to run unsynchronized against the same session row.
 */
@RunWith(RobolectricTestRunner::class)
class SessionFsmConcurrencyTest {

    private lateinit var app: Application
    private lateinit var repository: SessionRepository

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(app)
        repository = SessionRepository(app)
        runBlocking { repository.clearAll() }
    }

    /** From `Monitoring`, both `OutOfBedConfirmed` and `AlarmDismissed` transition to `Awake` alone,
     *  but whichever runs second should see the *other's* completed write and continue on to
     *  `Complete` -- so every valid serialization of the two converges on the same final status.
     *  Without the fix, both instead read the same stale `Monitoring` snapshot and independently
     *  compute `Awake`, so the final status wrongly stays `Awake` regardless of write order. */
    @Test
    fun concurrent_events_from_independent_fsm_instances_serialize_instead_of_racing() = runBlocking {
        val plan = Fixtures.dayPlan()
        val session = repository.createSession(plan, Fixtures.DATE, "Europe/Paris")
        repository.updateStatus(session.id, SessionStatus.Monitoring)

        val outOfBed = SessionEvent(
            trigger = TriggerType.OutOfBedConfirmed,
            timestamp = Fixtures.T0,
            data = EventData.OutOfBedConfirmed(evidence = listOf("test")),
        )
        val dismissed = SessionEvent(
            trigger = TriggerType.AlarmDismissed,
            timestamp = Fixtures.T0 + 1.seconds,
            data = EventData.Empty,
        )

        // Two fresh instances, exactly like two different call sites racing on the same session.
        val fsmA = SessionFsm(app, repository)
        val fsmB = SessionFsm(app, repository)

        listOf(
            async(Dispatchers.Default) { fsmA.onEvent(outOfBed) },
            async(Dispatchers.Default) { fsmB.onEvent(dismissed) },
        ).awaitAll()

        assertEquals(SessionStatus.Complete, repository.findById(session.id)?.status)
    }
}
