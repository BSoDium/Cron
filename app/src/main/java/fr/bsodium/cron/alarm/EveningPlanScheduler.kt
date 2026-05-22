package fr.bsodium.cron.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import fr.bsodium.cron.receiver.EveningPlanReceiver
import fr.bsodium.cron.settings.SettingsRepository
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/**
 * Arms the daily "start sleep session" trigger.
 *
 * Uses [AlarmManager.setAlarmClock] which:
 *  - is exempt from Doze,
 *  - is exempt from the SCHEDULE_EXACT_ALARM permission grant requirement
 *    (USE_EXACT_ALARM covers alarm-clock apps).
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

    /** Arm the next evening trigger. Resolves the local time at arm-time so DST is correct. */
    suspend fun armNext() {
        val triggerLocal = settings.currentEveningTriggerLocalTime()
        val tz = TimeZone.currentSystemDefault()
        val now = Clock.System.now().toLocalDateTime(tz)
        val firstCandidate = LocalDateTime(now.date, triggerLocal)
        val targetDate: LocalDate = if (firstCandidate <= now) {
            now.date.plus(1, DateTimeUnit.DAY)
        } else now.date
        val targetInstant = LocalDateTime(targetDate, triggerLocal).toInstant(tz)

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            targetInstant.toEpochMilliseconds(),
            pendingIntent(create = true)!!,
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
