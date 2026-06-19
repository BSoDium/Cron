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
import kotlinx.datetime.Instant
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
        // Auto-plan off = full stand-down: drop every automatic event. A manual run (the "run one
        // yourself" CTA) carries isManual and is exempt, so it still works with the toggle off.
        if (!SettingsRepository(context).autoAlarmsEnabledNow() &&
            (event.data as? EventData.EveningPlan)?.isManual != true
        ) {
            Log.d(TAG, "Auto-plan disabled — ignoring ${event.trigger}")
            return@withContext null
        }
        var current = repository.findCurrent()
        // A fresh evening plan for a new morning supersedes any session left unfinished from a prior day.
        if (event.trigger == TriggerType.EveningPlan && current != null && supersedeIfStale(current, event)) {
            current = null
        }
        val session = current
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

        if (shouldTriggerAi(event.trigger, nextStatus, session.lastAiCallAt)) {
            // Stamp the trigger time before enqueueing so the cooldown is effective immediately —
            // a burst of noisy events otherwise each slip through before the worker's tool sets it.
            repository.markAiTriggered(session.id)
            repository.triggerAiTurn(session.id)
        }

        session.id
    }

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
            allowedCommuteModes = settings.allowedCommuteModes.first(),
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

    /**
     * Re-reads plan-affecting settings into the session's frozen plan so a manual replan honours
     * changes made after bootstrap (e.g. the user edited their preparation time). Re-arms the
     * hard-latest floor only if it moved. No-ops if nothing plan-affecting changed.
     */
    suspend fun refreshPlanFromSettings(sessionId: String) = withContext(Dispatchers.IO) {
        val session = repository.findById(sessionId) ?: return@withContext
        val settings = SettingsRepository(context)
        val refreshed = session.plan.copy(
            hardLatest = settings.currentHardLatestDefault(),
            wakeWindowStart = settings.freeDayWakeStart.first(),
            wakeWindowEnd = settings.freeDayWakeEnd.first(),
            commuteBufferMinutes = settings.commuteBufferMinutes.first(),
            preparationBufferMinutes = settings.preparationBufferMinutes.first(),
            allowedCommuteModes = settings.allowedCommuteModes.first(),
            generatedAt = Clock.System.now(),
        )
        // generatedAt always differs, so compare the settings-derived fields explicitly.
        val unchanged = refreshed.hardLatest == session.plan.hardLatest &&
            refreshed.wakeWindowStart == session.plan.wakeWindowStart &&
            refreshed.wakeWindowEnd == session.plan.wakeWindowEnd &&
            refreshed.commuteBufferMinutes == session.plan.commuteBufferMinutes &&
            refreshed.preparationBufferMinutes == session.plan.preparationBufferMinutes &&
            refreshed.allowedCommuteModes == session.plan.allowedCommuteModes
        if (unchanged) return@withContext
        repository.updatePlan(sessionId, refreshed)
        if (refreshed.hardLatest != session.plan.hardLatest) {
            hardLatestScheduler.arm(
                hardLatest = refreshed.hardLatest,
                sessionDate = session.date,
                timezone = TimeZone.of(session.timezone),
                sessionId = sessionId,
            )
        }
        Log.i(TAG, "Session $sessionId plan refreshed from settings (prep=${refreshed.preparationBufferMinutes}, commute=${refreshed.commuteBufferMinutes})")
    }

    /**
     * If the current session targets a different morning than this evening plan, it never completed
     * (e.g. the alarm was never dismissed). Mark it Complete and clear its alarms so tonight starts
     * fresh. We deliberately do NOT stop the service here — it's the live FGS bootstrapping the new
     * session; the same-night re-plan case (matching date) is left untouched as a legitimate replan.
     */
    private suspend fun supersedeIfStale(current: SleepSession, eveningPlanEvent: SessionEvent): Boolean {
        val data = eveningPlanEvent.data as? EventData.EveningPlan ?: return false
        val morning = SessionRepository.morningDate(eveningPlanEvent.timestamp, TimeZone.of(data.timezone))
        if (current.date == morning) return false
        repository.updateStatus(current.id, SessionStatus.Complete)
        alarmScheduler.cancel(current.date)
        hardLatestScheduler.clear(current.date)
        Log.i(TAG, "Superseded stale session ${current.id} (date=${current.date}) for morning $morning")
        return true
    }

    private fun transition(session: SleepSession, event: SessionEvent): SessionStatus =
        when (event.trigger) {
            TriggerType.AlarmDismissed -> when (session.status) {
                SessionStatus.Monitoring, SessionStatus.ReMonitoring -> SessionStatus.Awake
                SessionStatus.Planning, SessionStatus.Awake, SessionStatus.Complete -> SessionStatus.Complete
            }
            TriggerType.SleepOnset -> when (session.status) {
                SessionStatus.Planning, SessionStatus.Monitoring -> SessionStatus.Monitoring
                SessionStatus.Awake -> SessionStatus.ReMonitoring
                else -> session.status // already past monitoring: no change
            }
            TriggerType.OutOfBedConfirmed -> when (session.status) {
                SessionStatus.Monitoring, SessionStatus.ReMonitoring -> SessionStatus.Awake
                SessionStatus.Awake -> SessionStatus.Complete
                else -> session.status
            }
            else -> session.status // other triggers don't change status
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
            else -> {} // Planning/Monitoring/ReMonitoring: no status-change side effect
        }
    }

    /**
     * Handle an alarm snooze, called from AlarmReceiver and bypassing the main
     * onEvent path. Returns true if AI was triggered for a replan, false if
     * snooze count ≥ 3 bypassed AI and scheduled now + 5 min directly.
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

        /** Triggers that recur on noise (screen-on, HC polls) and so are rate-limited by [AI_COOLDOWN]. */
        private val THROTTLEABLE_TRIGGERS = setOf(
            TriggerType.MidSleepActivity,
            TriggerType.HcStageUpdate,
        )

        private val AI_COOLDOWN = 15.minutes

        /**
         * Whether an event should fire an AI turn. Pure (no IO) so it's unit-testable: a completed
         * session never fires; only [AI_TRIGGERS] fire; and [THROTTLEABLE_TRIGGERS] are suppressed
         * within [AI_COOLDOWN] of the last AI turn so noisy sensors can't storm paid calls.
         */
        internal fun shouldTriggerAi(
            trigger: TriggerType,
            newStatus: SessionStatus,
            lastAiCallAt: Instant?,
            now: Instant = Clock.System.now(),
        ): Boolean {
            if (newStatus == SessionStatus.Complete) return false
            if (trigger !in AI_TRIGGERS) return false
            if (trigger in THROTTLEABLE_TRIGGERS && lastAiCallAt != null &&
                now - lastAiCallAt < AI_COOLDOWN
            ) {
                return false
            }
            return true
        }
    }
}
