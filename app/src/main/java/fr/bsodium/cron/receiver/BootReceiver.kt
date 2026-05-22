package fr.bsodium.cron.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import fr.bsodium.cron.alarm.EveningPlanScheduler
import fr.bsodium.cron.alarm.HardLatestScheduler
import fr.bsodium.cron.session.db.CronDatabase
import fr.bsodium.cron.session.db.SessionJson
import fr.bsodium.cron.session.model.DayPlan
import fr.bsodium.cron.session.model.SessionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

/**
 * Re-arms persistent alarms after device reboot.
 *
 * AlarmManager state is cleared by a reboot. We re-arm:
 *  1. The next evening plan trigger via [EveningPlanScheduler].
 *  2. The hard-latest alarm, **only if** an active (non-complete) session
 *     exists in the database and its hard-latest is still in the future.
 *
 * The mutable AI alarm is intentionally NOT re-armed here — the next
 * sensor event will trigger a fresh AI replan, which will re-arm it
 * naturally. Hard-latest stands as the safety floor in the meantime.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                EveningPlanScheduler(context).armNext()
                rearmActiveHardLatest(context)
            } catch (t: Throwable) {
                Log.e(TAG, "Boot re-arm failed", t)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun rearmActiveHardLatest(context: Context) {
        val db = CronDatabase.get(context)
        val session = db.sessionDao().findCurrent() ?: return
        if (session.status == SessionStatus.Complete.name) return

        val plan = runCatching { SessionJson.decodeFromString<DayPlan>(session.planJson) }.getOrNull()
            ?: return
        val tz = TimeZone.of(session.timezone)
        val sessionDate = runCatching { LocalDate.parse(session.date) }.getOrNull() ?: run {
            Log.w(TAG, "Skipping hard-latest re-arm: unparseable date '${session.date}'")
            return
        }
        val target = LocalDateTime(
            sessionDate.year, sessionDate.monthNumber, sessionDate.dayOfMonth,
            plan.hardLatest.hour, plan.hardLatest.minute, plan.hardLatest.second, plan.hardLatest.nanosecond,
        ).toInstant(tz)

        if (target <= Clock.System.now()) {
            Log.i(TAG, "Skipping hard-latest re-arm: target $target already past")
            return
        }

        HardLatestScheduler(context).arm(
            hardLatest = plan.hardLatest,
            sessionDate = sessionDate,
            timezone = tz,
            sessionId = session.id,
        )
        Log.i(TAG, "Re-armed hard-latest for session ${session.id} at $target")
    }

    private companion object {
        const val TAG = "BootReceiver"
    }
}
