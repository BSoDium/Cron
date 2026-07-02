package fr.bsodium.cron.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import fr.bsodium.cron.alarm.AlarmScheduler
import fr.bsodium.cron.alarm.EveningPlanScheduler
import fr.bsodium.cron.alarm.HardLatestScheduler
import fr.bsodium.cron.service.SleepSessionService
import fr.bsodium.cron.session.db.CronDatabase
import fr.bsodium.cron.session.db.SessionEntity
import fr.bsodium.cron.session.db.SessionJson
import fr.bsodium.cron.session.model.DayPlan
import fr.bsodium.cron.session.model.Instruction
import fr.bsodium.cron.session.model.SessionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant

/**
 * Restores session state after device reboot: AlarmManager entries and the
 * sensing foreground service are both wiped by a reboot, so we re-arm:
 *  1. The next evening plan trigger via [EveningPlanScheduler].
 *  2. For an active (non-complete) session: [SleepSessionService], so sensor
 *     monitoring resumes (a plain start intent — never re-runs the evening
 *     plan); the hard-latest safety alarm; and the AI-planned wake alarm
 *     persisted in the session's current instruction, when one was decided.
 *
 * Without the service restart no sensor event could ever fire again, so no
 * replan would re-arm anything — a mid-session reboot would silently degrade
 * the night to the hard-latest safety floor.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                EveningPlanScheduler(context).armNext()
                recoverActiveSession(context)
            } catch (t: Throwable) {
                Log.e(TAG, "Boot re-arm failed", t)
            } finally {
                pending.finish()
            }
        }
    }

    internal suspend fun recoverActiveSession(context: Context) {
        val session = CronDatabase.get(context).sessionDao().findCurrent() ?: return
        if (session.status == SessionStatus.Complete.name) return

        context.startService(SleepSessionService.startIntent(context))
        Log.i(TAG, "Restarted sleep-session service for session ${session.id}")

        val plan = runCatching { SessionJson.decodeFromString<DayPlan>(session.planJson) }
            .onFailure { Log.w(TAG, "Skipping alarm re-arm: unparseable plan", it) }
            .getOrNull() ?: return
        val sessionDate = runCatching { LocalDate.parse(session.date) }
            .onFailure { Log.w(TAG, "Skipping alarm re-arm: unparseable date '${session.date}'", it) }
            .getOrNull() ?: return
        val timezone = TimeZone.of(session.timezone)

        rearmHardLatest(context, plan, sessionDate, timezone, session.id)
        rearmPlannedAlarm(context, session, plan, sessionDate, timezone)
    }

    private fun rearmHardLatest(
        context: Context,
        plan: DayPlan,
        sessionDate: LocalDate,
        timezone: TimeZone,
        sessionId: String,
    ) {
        val target = sessionDate.atTime(plan.hardLatest).toInstant(timezone)
        if (target <= Clock.System.now()) {
            Log.i(TAG, "Skipping hard-latest re-arm: target $target already past")
            return
        }
        HardLatestScheduler(context).arm(
            hardLatest = plan.hardLatest,
            sessionDate = sessionDate,
            timezone = timezone,
            sessionId = sessionId,
        )
        Log.i(TAG, "Re-armed hard-latest for session $sessionId at $target")
    }

    private fun rearmPlannedAlarm(
        context: Context,
        session: SessionEntity,
        plan: DayPlan,
        sessionDate: LocalDate,
        timezone: TimeZone,
    ) {
        val instruction = runCatching { SessionJson.decodeFromString<Instruction>(session.currentInstructionJson) }
            .onFailure { Log.w(TAG, "Skipping planned-alarm re-arm: unparseable instruction", it) }
            .getOrNull() ?: return
        // A null alarmTime means no AI decision has armed an alarm (CancelAlarm resets it,
        // DoNothing carries it forward) — hard-latest alone is the correct state.
        val alarmTime = instruction.alarmTime ?: return
        val requested = sessionDate.atTime(alarmTime).toInstant(timezone)
        if (requested <= Clock.System.now()) {
            Log.i(TAG, "Skipping planned-alarm re-arm: target $requested already past")
            return
        }
        AlarmScheduler(context).schedule(
            requested = requested,
            hardLatest = plan.hardLatest,
            sessionDate = sessionDate,
            timezone = timezone,
            label = "Wake up",
            sessionId = session.id,
        )
        Log.i(TAG, "Re-armed planned alarm for session ${session.id} at $requested")
    }

    private companion object {
        const val TAG = "BootReceiver"
    }
}
