package fr.bsodium.cron.session

import fr.bsodium.cron.session.model.SessionStatus
import fr.bsodium.cron.session.model.TriggerType
import kotlinx.datetime.Instant
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.minutes

class SessionFsmTest {

    private val now = Instant.parse("2026-05-22T03:00:00Z")

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
