package fr.bsodium.cron.session

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.WorkManagerTestInitHelper
import fr.bsodium.cron.alarm.AlarmScheduler
import fr.bsodium.cron.session.model.EventData
import fr.bsodium.cron.session.model.SessionEvent
import fr.bsodium.cron.session.model.SessionStatus
import fr.bsodium.cron.session.model.TriggerType
import fr.bsodium.cron.testutil.Fixtures
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.time.Duration.Companion.seconds

/** Regression for #154: `incrementSnoozeCount` and the ≥3 escalation branch. */
@RunWith(RobolectricTestRunner::class)
class SessionFsmSnoozeTest {

    private lateinit var app: Application
    private lateinit var repository: SessionRepository

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(app)
        repository = SessionRepository(app)
        runBlocking { repository.clearAll() }
    }

    private fun snoozeEvent(at: Instant, count: Int) = SessionEvent(
        trigger = TriggerType.AlarmSnoozed,
        timestamp = at,
        data = EventData.AlarmInteraction(snoozeDurationMinutes = 10, snoozeCount = count),
    )

    /** A double-delivered snooze broadcast or rapid double-slide both incrementing from the same
     *  stale read used to lose an increment before the DAO-level atomic UPDATE landed with #153's PR
     *  (7bc8df3) — never regression-locked at the time. Both must land here. */
    @Test
    fun concurrent_snoozes_from_independent_fsm_instances_both_land() = runBlocking {
        val plan = Fixtures.dayPlan()
        val session = repository.createSession(plan, Fixtures.DATE, "Europe/Paris")
        repository.updateStatus(session.id, SessionStatus.Monitoring)

        val fsmA = SessionFsm(app, repository)
        val fsmB = SessionFsm(app, repository)

        listOf(
            async(Dispatchers.Default) { fsmA.onSnooze(session, snoozeEvent(Fixtures.T0, 1)) },
            async(Dispatchers.Default) { fsmB.onSnooze(session, snoozeEvent(Fixtures.T0 + 1.seconds, 1)) },
        ).awaitAll()

        assertEquals(2, repository.findById(session.id)?.snoozeCount)
    }

    /** The third snooze crosses the ≥3 threshold: AI is bypassed and a fallback alarm is armed
     *  directly, using the session object the caller already had (#154) rather than re-fetching it. */
    @Test
    fun snooze_reaching_threshold_bypasses_ai_and_arms_the_fallback_alarm() = runBlocking {
        val plan = Fixtures.dayPlan()
        val session = repository.createSession(plan, Fixtures.DATE, "Europe/Paris")
        repository.updateStatus(session.id, SessionStatus.Monitoring)
        val fsm = SessionFsm(app, repository)

        assertTrue(fsm.onSnooze(session, snoozeEvent(Fixtures.T0, 1)))
        assertTrue(fsm.onSnooze(session, snoozeEvent(Fixtures.T0 + 1.seconds, 2)))
        assertFalse(fsm.onSnooze(session, snoozeEvent(Fixtures.T0 + 2.seconds, 3)))

        assertTrue(AlarmScheduler(app).isArmed(session.date))
    }
}
