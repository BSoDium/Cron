package fr.bsodium.cron.session

import fr.bsodium.cron.session.model.EventData
import fr.bsodium.cron.session.model.SessionEvent
import fr.bsodium.cron.session.model.SessionStatus
import fr.bsodium.cron.session.model.TriggerType
import fr.bsodium.cron.testutil.Fixtures
import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.minutes

class SessionFsmTest {

    private val now = Instant.parse("2026-05-22T03:00:00Z")

    private val alarmDismissed = SessionEvent(
        trigger = TriggerType.AlarmDismissed,
        timestamp = now,
        data = EventData.Empty,
    )

    private val outOfBedConfirmed = SessionEvent(
        trigger = TriggerType.OutOfBedConfirmed,
        timestamp = now,
        data = EventData.OutOfBedConfirmed(evidence = listOf("test")),
    )

    @Test
    fun alarm_dismissed_from_monitoring_rearms_to_awake() {
        val session = Fixtures.session(status = SessionStatus.Monitoring)
        assertEquals(SessionStatus.Awake, SessionFsm.transition(session, alarmDismissed))
    }

    @Test
    fun alarm_dismissed_from_remonitoring_rearms_to_awake() {
        val session = Fixtures.session(status = SessionStatus.ReMonitoring)
        assertEquals(SessionStatus.Awake, SessionFsm.transition(session, alarmDismissed))
    }

    @Test
    fun alarm_dismissed_from_awake_completes_session() {
        val session = Fixtures.session(status = SessionStatus.Awake)
        assertEquals(SessionStatus.Complete, SessionFsm.transition(session, alarmDismissed))
    }

    @Test
    fun out_of_bed_confirmed_from_awake_completes_session() {
        val session = Fixtures.session(status = SessionStatus.Awake)
        assertEquals(SessionStatus.Complete, SessionFsm.transition(session, outOfBedConfirmed))
    }

    @Test
    fun out_of_bed_confirmed_from_monitoring_wakes_up() {
        val session = Fixtures.session(status = SessionStatus.Monitoring)
        assertEquals(SessionStatus.Awake, SessionFsm.transition(session, outOfBedConfirmed))
    }

    @Test
    fun completed_session_never_fires() {
        assertFalse(
            SessionFsm.shouldTriggerAi(TriggerType.SleepOnset, SessionStatus.Complete, null, now),
        )
    }

    @Test
    fun non_ai_trigger_never_fires() {
        assertFalse(
            SessionFsm.shouldTriggerAi(TriggerType.AlarmDismissed, SessionStatus.Monitoring, null, now),
        )
    }

    @Test
    fun state_changing_trigger_fires_even_right_after_a_turn() {
        assertTrue(
            SessionFsm.shouldTriggerAi(TriggerType.SleepOnset, SessionStatus.Monitoring, now, now),
        )
    }

    @Test
    fun throttleable_trigger_suppressed_within_cooldown() {
        val lastCall = now - 5.minutes
        assertFalse(
            SessionFsm.shouldTriggerAi(TriggerType.MidSleepActivity, SessionStatus.Monitoring, lastCall, now),
        )
    }

    @Test
    fun throttleable_trigger_fires_after_cooldown() {
        val lastCall = now - 20.minutes
        assertTrue(
            SessionFsm.shouldTriggerAi(TriggerType.MidSleepActivity, SessionStatus.Monitoring, lastCall, now),
        )
    }

    @Test
    fun throttleable_trigger_fires_when_no_prior_turn() {
        assertTrue(
            SessionFsm.shouldTriggerAi(TriggerType.MidSleepActivity, SessionStatus.Monitoring, null, now),
        )
    }
}
