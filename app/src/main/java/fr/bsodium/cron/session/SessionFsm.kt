package fr.bsodium.cron.session

import android.content.Context
import android.util.Log
import fr.bsodium.cron.alarm.AlarmScheduler
import fr.bsodium.cron.alarm.HardLatestScheduler
import fr.bsodium.cron.session.model.DayPlan
import fr.bsodium.cron.session.model.EventData
import fr.bsodium.cron.session.model.SessionEvent
import fr.bsodium.cron.session.model.SessionStatus
import fr.bsodium.cron.session.model.SleepSession
import fr.bsodium.cron.session.model.TriggerType
import fr.bsodium.cron.service.SleepSessionService
import fr.bsodium.cron.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlin.time.Duration.Companion.minutes

/**
 * Finite state machine for a single sleep session.
 *
 * Responsibilities:
 *  - Persisting sensor events via [SessionRepository]
 *  - Driving status transitions (planning → monitoring → awake → ...)
 *  - Arming / clearing the hard-latest alarm on session boundaries
 *  - Triggering AI turns on meaningful events
 *  - Handling snooze escalation (count ≥ 3 → bypass AI)
 *
 * All methods are suspend and should be called from a background
 * coroutine or a [goAsync] BroadcastReceiver scope.
 */
class SessionFsm(
    private val context: Context,
    private val repository: SessionRepository,
) {
    private val alarmScheduler = AlarmScheduler(context)
    private val hardLatestScheduler = HardLatestScheduler(context)

    /**
     * Deliver [event] to the FSM. Returns the session id, or null if no
     * session exists and the event cannot bootstrap one.
     */
    suspend fun onEvent(event: SessionEvent): String? = withContext(Dispatchers.IO) {
        val session = repository.findCurrent()
            ?: if (event.trigger == TriggerType.EveningPlan) {
                bootstrapSession(event) ?: return@withContext null
            } else {
                Log.w(TAG, "Received ${event.trigger} but no active session — ignoring")
                return@withContext null
            }

        if (session.status == SessionStatus.Complete) {
            Log.d(TAG, "Session ${session.id} complete — ignoring ${event.trigger}")
            return@withContext session.id
        }

        repository.appendEvent(session.id, event)

        val nextStatus = transition(session, event)
        if (nextStatus != session.status) {
            repository.updateStatus(session.id, nextStatus)
            onStatusChange(session, nextStatus)
        }

        if (shouldTriggerAi(event.trigger, nextStatus)) {
            repository.triggerAiTurn(session.id)
        }

        session.id
    }

    // ---------------------------------------------------------------------------
    // Session bootstrap
    // ---------------------------------------------------------------------------

    private suspend fun bootstrapSession(eveningPlanEvent: SessionEvent): SleepSession? {
        val data = eveningPlanEvent.data as? EventData.EveningPlan ?: return null
        val tz = TimeZone.of(data.timezone)
        val settings = SettingsRepository(context)

        val plan = DayPlan(
            hardLatest = settings.currentHardLatestDefault(),
            wakeWindowStart = settings.freeDayWakeStart.first(),
            wakeWindowEnd = settings.freeDayWakeEnd.first(),
            commuteBufferMinutes = settings.commuteBufferMinutes.first(),
            preparationBufferMinutes = settings.preparationBufferMinutes.first(),
            isFreeDayFallback = true,
            generatedAt = Clock.System.now(),
        )
        val date = SessionRepository.morningDate(eveningPlanEvent.timestamp, tz)
        val session = repository.createSession(plan, date, data.timezone)

        // Arm hard-latest immediately — the safety floor is non-negotiable.
        hardLatestScheduler.arm(
            hardLatest = plan.hardLatest,
            sessionDate = date,
            timezone = tz,
            sessionId = session.id,
        )
        Log.i(TAG, "Session ${session.id} created for $date, hard-latest=${plan.hardLatest}")
        return session
    }

    // ---------------------------------------------------------------------------
    // State transitions
    // ---------------------------------------------------------------------------

    private fun transition(session: SleepSession, event: SessionEvent): SessionStatus =
        when (event.trigger) {
            TriggerType.AlarmDismissed -> SessionStatus.Complete
            TriggerType.SleepOnset -> when (session.status) {
                SessionStatus.Planning, SessionStatus.Monitoring -> SessionStatus.Monitoring
                SessionStatus.Awake -> SessionStatus.ReMonitoring
                else -> session.status
            }
            TriggerType.OutOfBedConfirmed -> when (session.status) {
                SessionStatus.Monitoring, SessionStatus.ReMonitoring -> SessionStatus.Awake
                else -> session.status
            }
            else -> session.status
        }

    private fun onStatusChange(session: SleepSession, newStatus: SessionStatus) {
        when (newStatus) {
            SessionStatus.Awake -> {
                context.startService(SleepSessionService.rearmIntent(context))
                Log.i(TAG, "Session ${session.id} → Awake — rearming sleep monitor")
            }
            SessionStatus.Complete -> {
                alarmScheduler.cancel(session.date)
                hardLatestScheduler.clear(session.date)
                context.startService(SleepSessionService.stopIntent(context))
                Log.i(TAG, "Session ${session.id} complete — alarms cleared, service stopped")
            }
            else -> {}
        }
    }

    // ---------------------------------------------------------------------------
    // AI trigger policy
    // ---------------------------------------------------------------------------

    private fun shouldTriggerAi(trigger: TriggerType, newStatus: SessionStatus): Boolean {
        if (newStatus == SessionStatus.Complete) return false
        return trigger in AI_TRIGGERS
    }

    // ---------------------------------------------------------------------------
    // Snooze escalation (called from AlarmReceiver, bypasses the main onEvent path)
    // ---------------------------------------------------------------------------

    /**
     * Handle an alarm snooze. Returns true if AI was triggered for a replan,
     * false if snooze count ≥ 3 bypassed AI and scheduled now + 5 min directly.
     */
    suspend fun onSnooze(sessionId: String, event: SessionEvent): Boolean =
        withContext(Dispatchers.IO) {
            repository.appendEvent(sessionId, event)
            val newCount = repository.incrementSnoozeCount(sessionId)

            if (newCount >= 3) {
                val session = repository.findById(sessionId) ?: return@withContext false
                val tz = TimeZone.of(session.timezone)
                alarmScheduler.schedule(
                    requested = Clock.System.now() + 5.minutes,
                    hardLatest = session.plan.hardLatest,
                    sessionDate = session.date,
                    timezone = tz,
                    label = "Wake up",
                    sessionId = sessionId,
                )
                Log.i(TAG, "Snooze count $newCount ≥ 3 — AI bypassed, alarm in 5 min")
                false
            } else {
                repository.triggerAiTurn(sessionId)
                true
            }
        }

    companion object {
        private const val TAG = "SessionFsm"

        private val AI_TRIGGERS = setOf(
            TriggerType.EveningPlan,
            TriggerType.SleepOnset,
            TriggerType.HcStageUpdate,
            TriggerType.MidSleepActivity,
            TriggerType.OutOfBedConfirmed,
            TriggerType.WakeWindowOpportunity,
            TriggerType.CalendarChange,
            TriggerType.AlarmSnoozed,
        )
    }
}
