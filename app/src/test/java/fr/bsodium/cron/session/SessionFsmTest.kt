package fr.bsodium.cron.session

import fr.bsodium.cron.session.model.EventData
import fr.bsodium.cron.session.model.SessionEvent
import fr.bsodium.cron.session.model.SessionStatus
import fr.bsodium.cron.session.model.TriggerType
import fr.bsodium.cron.testutil.Fixtures
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.hours
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

    private val sleepOnset = SessionEvent(
        trigger = TriggerType.SleepOnset,
        timestamp = now,
        data = EventData.Empty,
    )

    private val hardLatestFired = SessionEvent(
        trigger = TriggerType.HardLatestFired,
        timestamp = now,
        data = EventData.Empty,
    )

    private val calendarChange = SessionEvent(
        trigger = TriggerType.CalendarChange,
        timestamp = now,
        data = EventData.Empty,
    )

    @Test
    fun alarm_dismissed_from_monitoring_rearms_to_awake() {
        val session = Fixtures.session(status = SessionStatus.Monitoring)
        assertEquals(SessionStatus.Awake, SessionFsm.transition(session, alarmDismissed))
    }

    /** No prior dismiss in the event history means this dismiss is the morning wake — completing
     *  outright is the fix for the false-wake bug (a spurious OutOfBedConfirmed re-armed onset
     *  detection hours before the real dismissal). */
    @Test
    fun alarm_dismissed_from_remonitoring_with_no_prior_dismiss_completes_session() {
        val session = Fixtures.session(status = SessionStatus.ReMonitoring)
        assertEquals(SessionStatus.Complete, SessionFsm.transition(session, alarmDismissed))
    }

    @Test
    fun alarm_dismissed_from_remonitoring_long_after_a_prior_dismiss_completes_session() {
        val priorDismiss = alarmDismissed.copy(timestamp = now - 6.hours)
        val session = Fixtures.session(status = SessionStatus.ReMonitoring, events = listOf(priorDismiss))
        assertEquals(SessionStatus.Complete, SessionFsm.transition(session, alarmDismissed))
    }

    /** A rapid dismiss -> fall back asleep -> re-ring -> dismiss chain, all within the grace window — the legitimate re-ring flow, still re-arms rather than completing. */
    @Test
    fun alarm_dismissed_from_remonitoring_shortly_after_a_prior_dismiss_rearms_to_awake() {
        val priorDismiss = alarmDismissed.copy(timestamp = now - 10.minutes)
        val session = Fixtures.session(status = SessionStatus.ReMonitoring, events = listOf(priorDismiss))
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
    fun out_of_bed_confirmed_from_planning_is_a_no_op() {
        val session = Fixtures.session(status = SessionStatus.Planning)
        assertEquals(SessionStatus.Planning, SessionFsm.transition(session, outOfBedConfirmed))
    }

    @Test
    fun out_of_bed_confirmed_from_complete_is_a_no_op() {
        val session = Fixtures.session(status = SessionStatus.Complete)
        assertEquals(SessionStatus.Complete, SessionFsm.transition(session, outOfBedConfirmed))
    }

    @Test
    fun sleep_onset_from_remonitoring_is_a_no_op() {
        val session = Fixtures.session(status = SessionStatus.ReMonitoring)
        assertEquals(SessionStatus.ReMonitoring, SessionFsm.transition(session, sleepOnset))
    }

    @Test
    fun sleep_onset_from_complete_is_a_no_op() {
        val session = Fixtures.session(status = SessionStatus.Complete)
        assertEquals(SessionStatus.Complete, SessionFsm.transition(session, sleepOnset))
    }

    @Test
    fun hard_latest_fired_never_changes_status() {
        val session = Fixtures.session(status = SessionStatus.Monitoring)
        assertEquals(SessionStatus.Monitoring, SessionFsm.transition(session, hardLatestFired))
    }

    @Test
    fun calendar_change_never_changes_status() {
        val session = Fixtures.session(status = SessionStatus.ReMonitoring)
        assertEquals(SessionStatus.ReMonitoring, SessionFsm.transition(session, calendarChange))
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

    /** hardLatest=10:00 Europe/Paris on 2026-05-22 is 2026-05-22T08:00:00Z (CEST, UTC+2); + the 3h
     *  grace, its hard-latest ceiling is 11:00Z. Shared by the sessionWindowEnd/shouldGateEvent tests below. */
    private val hardLatestSession = Fixtures.session(plan = Fixtures.dayPlan(hardLatest = LocalTime(10, 0)))

    private fun withWakeEvent(at: Instant) = hardLatestSession.copy(
        events = listOf(SessionEvent(trigger = TriggerType.OutOfBedConfirmed, timestamp = at, data = EventData.OutOfBedConfirmed(evidence = listOf("test")))),
    )

    @Test
    fun within_window_via_hard_latest_ceiling_with_no_wake_event() {
        assertTrue(SessionFsm.isWithinActiveWindow(hardLatestSession, now = Fixtures.at("2026-05-22T10:00:00Z")))
    }

    @Test
    fun outside_window_via_hard_latest_ceiling_with_no_wake_event() {
        assertFalse(SessionFsm.isWithinActiveWindow(hardLatestSession, now = Fixtures.at("2026-05-22T12:00:00Z")))
    }

    /** The post-wake ceiling (05:00Z wake + 3h grace = 08:00Z) binds here since it's earlier than the
     *  11:00Z hard-latest ceiling -- confirms sessionWindowEnd takes whichever ceiling is earlier. */
    @Test
    fun within_window_via_the_earlier_post_wake_ceiling() {
        val session = withWakeEvent(Fixtures.at("2026-05-22T05:00:00Z"))
        assertTrue(SessionFsm.isWithinActiveWindow(session, now = Fixtures.at("2026-05-22T07:00:00Z")))
    }

    /** The lenient-hardLatest-day scenario the plan's revision 1 missed: the user woke at 05:00Z (post-
     *  wake ceiling 08:00Z) but hardLatest doesn't close until 11:00Z -- without taking the earlier
     *  ceiling, a 09:00Z nap would be misread as still within the original session's window. */
    @Test
    fun outside_window_via_the_earlier_post_wake_ceiling_despite_a_later_hard_latest() {
        val session = withWakeEvent(Fixtures.at("2026-05-22T05:00:00Z"))
        assertFalse(SessionFsm.isWithinActiveWindow(session, now = Fixtures.at("2026-05-22T09:00:00Z")))
    }

    /** Here the hard-latest ceiling (11:00Z) is the earlier/binding one despite a wake event, since
     *  that wake happened late (10:30Z, pushing its own ceiling to 13:30Z) -- either ceiling can bind. */
    @Test
    fun outside_window_via_the_earlier_hard_latest_ceiling_despite_a_later_wake_event() {
        val session = withWakeEvent(Fixtures.at("2026-05-22T10:30:00Z"))
        assertFalse(SessionFsm.isWithinActiveWindow(session, now = Fixtures.at("2026-05-22T11:30:00Z")))
    }

    @Test
    fun window_gated_trigger_outside_window_is_gated() {
        assertTrue(SessionFsm.shouldGateEvent(hardLatestSession, TriggerType.SleepOnset, now = Fixtures.at("2026-05-22T12:00:00Z")))
    }

    @Test
    fun window_gated_trigger_inside_window_is_not_gated() {
        assertFalse(SessionFsm.shouldGateEvent(hardLatestSession, TriggerType.SleepOnset, now = Fixtures.at("2026-05-22T10:00:00Z")))
    }

    /** AlarmDismissed/AlarmSnoozed/HardLatestFired are direct user actions or the safety net itself
     *  firing -- they must never be silently dropped, window or no window. */
    @Test
    fun direct_action_triggers_never_gate_regardless_of_window() {
        val outsideWindow = Fixtures.at("2026-05-22T12:00:00Z")
        assertFalse(SessionFsm.shouldGateEvent(hardLatestSession, TriggerType.AlarmDismissed, outsideWindow))
        assertFalse(SessionFsm.shouldGateEvent(hardLatestSession, TriggerType.AlarmSnoozed, outsideWindow))
        assertFalse(SessionFsm.shouldGateEvent(hardLatestSession, TriggerType.HardLatestFired, outsideWindow))
    }
}
