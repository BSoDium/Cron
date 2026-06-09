package fr.bsodium.cron.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import fr.bsodium.cron.receiver.EveningPlanReceiver
import fr.bsodium.cron.settings.SettingsRepository
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/** The next instant the evening-plan trigger fires for [trigger]: today's occurrence if still ahead,
 *  otherwise tomorrow's. Shared by [EveningPlanScheduler] (to arm) and the home UI (the "next plan at …"
 *  resting line) so both agree on when planning runs. */
internal fun nextEveningPlanInstant(trigger: LocalTime, now: Instant, tz: TimeZone): Instant {
    val nowLocal = now.toLocalDateTime(tz)
    val firstCandidate = LocalDateTime(nowLocal.date, trigger)
    val targetDate = if (firstCandidate <= nowLocal) nowLocal.date.plus(1, DateTimeUnit.DAY) else nowLocal.date
    return LocalDateTime(targetDate, trigger).toInstant(tz)
}

/**
 * Arms the daily "start sleep session" trigger.
 *
 * Uses [AlarmManager.setExactAndAllowWhileIdle] — exact and Doze-exempt, but (unlike setAlarmClock)
 * it does not surface a system alarm-clock indicator, which is correct for a silent background
 * planning trigger rather than a user-facing wake alarm. Exact scheduling is authorised by the
 * manifest's USE_EXACT_ALARM (this is an alarm-clock app), so no SCHEDULE_EXACT_ALARM grant is needed.
 *
 * Re-armed by:
 *  - [fr.bsodium.cron.receiver.BootReceiver] on `ACTION_BOOT_COMPLETED`
 *  - the [EveningPlanReceiver] itself after each fire (chained daily)
 *  - a future TimeZoneChangedReceiver (Phase 6)
 *  - settings-change call sites in [fr.bsodium.cron.ui.screens.settings]
 */
class EveningPlanScheduler(
    private val context: Context,
    private val settings: SettingsRepository = SettingsRepository(context),
) {

    private val alarmManager: AlarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /** Arm the next evening trigger. Resolves the local time at arm-time so DST is correct. When the
     *  user has disabled auto alarms this instead cancels any pending trigger — so every arm call site
     *  (boot, settings, timezone change, the chained re-arm) respects the toggle through one guard. */
    suspend fun armNext() {
        if (!settings.autoAlarmsEnabledNow()) {
            cancel()
            return
        }
        val triggerLocal = settings.currentEveningTriggerLocalTime()
        val tz = TimeZone.currentSystemDefault()
        val targetInstant = nextEveningPlanInstant(triggerLocal, Clock.System.now(), tz)

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            targetInstant.toEpochMilliseconds(),
            requireNotNull(pendingIntent(create = true)) { "Evening-plan PendingIntent is non-null when create = true" },
        )
    }

    fun cancel() {
        pendingIntent(create = false)?.let(alarmManager::cancel)
    }

    fun isArmed(): Boolean = pendingIntent(create = false) != null

    private fun pendingIntent(create: Boolean): PendingIntent? {
        val flags = if (create) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        }
        val intent = Intent(context, EveningPlanReceiver::class.java).apply {
            action = EveningPlanReceiver.ACTION_FIRE
        }
        return PendingIntent.getBroadcast(
            context, AlarmConstants.EVENING_PLAN_REQUEST_CODE, intent, flags,
        )
    }


}
