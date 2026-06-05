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
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/**
 * Schedules the **mutable** wake alarm decided by the AI.
 *
 * The AI may reschedule this alarm any number of times during the night;
 * each call replaces the previous arming for [sessionDate]. The alarm time
 * is always clamped client-side to `[now + MIN_LEAD_SECONDS, hardLatest]`
 * so the AI can never push it past the safety floor.
 *
 * See [HardLatestScheduler] for the independent safety alarm.
 */
class AlarmScheduler(private val context: Context) {

    data class ClampedSchedule(
        val requestedInstant: Instant,
        val actualInstant: Instant,
        val clampedToHardLatest: Boolean,
    )

    private val alarmManager: AlarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /**
     * Schedule (or replace) the AI alarm.
     *
     * @param requested the AI-requested fire time
     * @param hardLatest the session's immutable hard-latest local time
     * @param sessionDate the morning date the alarm belongs to (used for request code)
     * @param timezone the session's IANA timezone
     * @param label notification label
     * @param sessionId session this alarm belongs to (passed back via extras)
     */
    fun schedule(
        requested: Instant,
        hardLatest: LocalTime,
        sessionDate: LocalDate,
        timezone: TimeZone,
        label: String,
        sessionId: String,
    ): ClampedSchedule {
        val plan = clamp(requested, Clock.System.now(), hardLatest, sessionDate, timezone)

        val pi = requireNotNull(aiPendingIntent(sessionDate, label, sessionId, create = true)) {
            "AI alarm PendingIntent is non-null when create = true"
        }
        alarmManager.setAlarmClock(
            AlarmManager.AlarmClockInfo(plan.actualInstant.toEpochMilliseconds(), showIntent()),
            pi,
        )

        if (plan.clampedToHardLatest) {
            Log.w(TAG, "AI alarm clamped: requested=$requested actual=${plan.actualInstant} hardLatest=$hardLatest")
        }
        return plan
    }

    /** Cancel the AI alarm for [sessionDate]. Idempotent. */
    fun cancel(sessionDate: LocalDate) {
        aiPendingIntent(sessionDate, label = "", sessionId = "", create = false)?.let(alarmManager::cancel)
    }

    /** Returns true if an AI alarm is currently armed for [sessionDate]. */
    fun isArmed(sessionDate: LocalDate): Boolean =
        aiPendingIntent(sessionDate, label = "", sessionId = "", create = false) != null

    private fun aiPendingIntent(
        sessionDate: LocalDate,
        label: String,
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
            putExtra(AlarmConstants.EXTRA_KIND, AlarmConstants.KIND_AI)
            putExtra(AlarmConstants.EXTRA_LABEL, label)
            putExtra(AlarmConstants.EXTRA_SESSION_ID, sessionId)
            putExtra(AlarmReceiver.EXTRA_REQUEST_CODE, AlarmConstants.aiRequestCode(sessionDate))
            putExtra(AlarmReceiver.EXTRA_LABEL, label)
        }
        return PendingIntent.getBroadcast(
            context, AlarmConstants.aiRequestCode(sessionDate), intent, flags,
        )
    }

    private fun showIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        private const val TAG = "AlarmScheduler"
        val MIN_LEAD: kotlin.time.Duration = kotlin.time.Duration.parse("60s")

        /**
         * Pure clamp: the **session owns the alarm date**, the model only chooses the time-of-day.
         * The requested instant's local time-of-day is pinned onto [sessionDate], then bounded to
         * `[now + MIN_LEAD, hardLatest@sessionDate]`. Pinning means a model that emits the wrong date
         * (e.g. today instead of tomorrow's morning) can't arm the alarm on the wrong day.
         *
         * - If the pinned time is in the past or too close to [now], it slides up to `now + MIN_LEAD`.
         *   This is NOT considered a hard-latest clamp.
         * - If it exceeds the hard latest, it slides down to the hard latest and
         *   [ClampedSchedule.clampedToHardLatest] becomes true.
         * - `maxOf(lower, upper)` guards the degenerate case where `now` is already past the hard
         *   latest (`lower > upper`) so `coerceIn` can't throw.
         */
        fun clamp(
            requested: Instant,
            now: Instant,
            hardLatest: LocalTime,
            sessionDate: LocalDate,
            timezone: TimeZone,
        ): ClampedSchedule {
            val onDate = requested.toLocalDateTime(timezone).time.atDate(sessionDate).toInstant(timezone)
            val lower = now + MIN_LEAD
            val upper = hardLatest.atDate(sessionDate).toInstant(timezone)
            val actual = onDate.coerceIn(lower, maxOf(lower, upper))
            return ClampedSchedule(
                requestedInstant = requested,
                actualInstant = actual,
                clampedToHardLatest = actual == upper && onDate > upper,
            )
        }
    }
}

private fun LocalTime.atDate(date: LocalDate): kotlinx.datetime.LocalDateTime =
    kotlinx.datetime.LocalDateTime(date.year, date.monthNumber, date.dayOfMonth, hour, minute, second, nanosecond)
