package fr.bsodium.cron.session

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.WorkManagerTestInitHelper
import fr.bsodium.cron.alarm.AlarmScheduler
import fr.bsodium.cron.session.model.ActionType
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

    /** `onSnooze`'s whole body runs inside the companion-scoped mutex, so two near-simultaneous
     *  snoozes from independent FSM instances (a double-delivered broadcast, a rapid double-slide)
     *  serialize rather than race — this locks in that both still land once serialized, the same
     *  guarantee #153 already regression-locked for `onEvent`, now also covered for `onSnooze`. The
     *  DAO's own atomicity (the read-then-write #154 originally reported) is covered separately below,
     *  since the mutex here would mask a regression in that layer entirely. */
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

    /** #154's actual claim — `SessionDao.incrementSnoozeCount` is a single atomic `UPDATE`, not a
     *  read-then-write — tested against the repository directly, bypassing `SessionFsm`'s mutex
     *  entirely; that mutex would otherwise serialize the two calls and mask a regression here. */
    @Test
    fun concurrent_increments_at_the_repository_level_dont_lose_updates() = runBlocking {
        val plan = Fixtures.dayPlan()
        val session = repository.createSession(plan, Fixtures.DATE, "Europe/Paris")

        listOf(
            async(Dispatchers.Default) { repository.incrementSnoozeCount(session.id) },
            async(Dispatchers.Default) { repository.incrementSnoozeCount(session.id) },
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
        assertFalse(AlarmScheduler(app).isArmed(session.date))

        assertTrue(fsm.onSnooze(session, snoozeEvent(Fixtures.T0 + 1.seconds, 2)))
        assertFalse(AlarmScheduler(app).isArmed(session.date))

        assertFalse(fsm.onSnooze(session, snoozeEvent(Fixtures.T0 + 2.seconds, 3)))
        assertTrue(AlarmScheduler(app).isArmed(session.date))
    }

    /** #219: the ≥3 branch arms a real alarm but previously never wrote the matching [fr.bsodium.cron
     *  .session.model.Instruction], leaving Home's wake-time card showing "no alarm" over a real one. */
    @Test
    fun snooze_reaching_threshold_updates_the_displayed_instruction() = runBlocking {
        val plan = Fixtures.dayPlan()
        val session = repository.createSession(plan, Fixtures.DATE, "Europe/Paris")
        repository.updateStatus(session.id, SessionStatus.Monitoring)
        val fsm = SessionFsm(app, repository)

        fsm.onSnooze(session, snoozeEvent(Fixtures.T0, 1))
        fsm.onSnooze(session, snoozeEvent(Fixtures.T0 + 1.seconds, 2))
        fsm.onSnooze(session, snoozeEvent(Fixtures.T0 + 2.seconds, 3))

        val instruction = repository.findById(session.id)?.currentInstruction
        assertEquals(ActionType.SetAlarm, instruction?.action)
        assertTrue(instruction?.alarmTime != null)
    }
}
