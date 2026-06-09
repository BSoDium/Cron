package fr.bsodium.cron.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import fr.bsodium.cron.MainActivity
import fr.bsodium.cron.receiver.AlarmReceiver
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant

private const val TAG = "HardLatestScheduler"

/**
 * Schedules the **immutable** hard-latest alarm — the safety floor that
 * always fires regardless of what the AI decides. Once armed at session
 * start, it can only be removed by an explicit [clear] call, which the
 * FSM only triggers on session completion or alarm dismissal.
 *
 * Uses its own request code distinct from [AlarmScheduler] so the two
 * alarms are independent AlarmManager entries.
 */
class HardLatestScheduler(private val context: Context) {

    private val alarmManager: AlarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /** Arms the hard-latest alarm and returns its target instant. A target already in the past is NOT
     *  armed (a past `setAlarmClock` fires immediately) — logged and skipped instead. */
    fun arm(
        hardLatest: LocalTime,
        sessionDate: LocalDate,
        timezone: TimeZone,
        sessionId: String,
    ): Instant {
        val target = sessionDate.atTime(hardLatest).toInstant(timezone)
        if (target <= Clock.System.now()) {
            Log.w(TAG, "Skipping hard-latest arm: target $target is already past")
            return target
        }

        val pi = requireNotNull(pendingIntent(sessionDate, sessionId, create = true)) {
            "Hard-latest PendingIntent is non-null when create = true"
        }
        alarmManager.setAlarmClock(
            AlarmManager.AlarmClockInfo(target.toEpochMilliseconds(), showIntent()),
            pi,
        )
        return target
    }

    /** Returns true if a hard-latest alarm is currently armed for [sessionDate]. */
    fun isArmed(sessionDate: LocalDate): Boolean =
        pendingIntent(sessionDate, sessionId = "", create = false) != null

    /** Idempotent removal of the hard-latest alarm. Use sparingly — only on session end. */
    fun clear(sessionDate: LocalDate) {
        pendingIntent(sessionDate, sessionId = "", create = false)?.let(alarmManager::cancel)
    }

    private fun pendingIntent(
        sessionDate: LocalDate,
        sessionId: String,
        create: Boolean,
    ): PendingIntent? {
        val flags = if (create) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        }
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_ALARM_FIRED
            putExtra(AlarmConstants.EXTRA_KIND, AlarmConstants.KIND_HARD_LATEST)
            putExtra(AlarmConstants.EXTRA_LABEL, "Wake up (hard latest)")
            putExtra(AlarmConstants.EXTRA_SESSION_ID, sessionId)
            putExtra(AlarmReceiver.EXTRA_REQUEST_CODE, AlarmConstants.hardLatestRequestCode(sessionDate))
            putExtra(AlarmReceiver.EXTRA_LABEL, "Wake up (hard latest)")
        }
        return PendingIntent.getBroadcast(
            context, AlarmConstants.hardLatestRequestCode(sessionDate), intent, flags,
        )
    }

    private fun showIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
