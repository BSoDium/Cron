package fr.bsodium.cron.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import fr.bsodium.cron.receiver.SessionExpiryReceiver
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

private const val TAG = "SessionExpiryScheduler"

/**
 * Schedules the silent janitorial alarm that closes a session with no further activity past its
 * active window (`SessionFsm.sessionWindowEnd`). Uses [AlarmManager.setExactAndAllowWhileIdle], not
 * [AlarmManager.setAlarmClock] like [HardLatestScheduler] — this timer must never be user-visible.
 * Once the real hard-latest alarm fires and clears, a cleanup timer left on `setAlarmClock` would
 * become the system's displayed "next alarm" on the lock screen, a phantom time the user never set.
 * `setExactAndAllowWhileIdle` is exact and Doze-exempt with no UI surface, and needs no extra
 * permission beyond the manifest's existing `USE_EXACT_ALARM`.
 */
class SessionExpiryScheduler(private val context: Context) {

    private val alarmManager: AlarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /** Arms the expiry check for [target]. A target already in the past is not armed — logged and
     *  skipped, matching [HardLatestScheduler.arm]'s handling of the same edge case. */
    fun arm(target: Instant, sessionDate: LocalDate, sessionId: String) {
        if (target <= Clock.System.now()) {
            Log.w(TAG, "Skipping session-expiry arm: target $target is already past")
            return
        }
        val pi = requireNotNull(pendingIntent(sessionDate, sessionId, create = true)) {
            "Session-expiry PendingIntent is non-null when create = true"
        }
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, target.toEpochMilliseconds(), pi)
    }

    fun isArmed(sessionDate: LocalDate): Boolean =
        pendingIntent(sessionDate, sessionId = "", create = false) != null

    /** Idempotent removal of the expiry alarm. */
    fun clear(sessionDate: LocalDate) {
        pendingIntent(sessionDate, sessionId = "", create = false)?.let(alarmManager::cancel)
    }

    private fun pendingIntent(sessionDate: LocalDate, sessionId: String, create: Boolean): PendingIntent? {
        val flags = if (create) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        }
        val intent = Intent(context, SessionExpiryReceiver::class.java).apply {
            action = SessionExpiryReceiver.ACTION_FIRE
            putExtra(AlarmConstants.EXTRA_SESSION_ID, sessionId)
        }
        return PendingIntent.getBroadcast(
            context, AlarmConstants.sessionExpiryRequestCode(sessionDate), intent, flags,
        )
    }
}
