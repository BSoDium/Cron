package fr.bsodium.cron.engine.scheduler

import android.app.AlarmManager
import android.app.AlarmManager.AlarmClockInfo
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import fr.bsodium.cron.engine.model.ScheduledAlarm
import fr.bsodium.cron.receiver.AlarmReceiver

/**
 * Schedules and cancels alarms using Android's [AlarmManager].
 *
 * Uses [AlarmManager.setAlarmClock] which:
 * - Is exempt from Doze mode restrictions
 * - Shows an alarm icon in the status bar
 * - On Android 12+, does not require SCHEDULE_EXACT_ALARM (uses USE_EXACT_ALARM semantics)
 */
class AlarmSchedulerImpl(
    private val context: Context
) : AlarmScheduler {

    private val alarmManager: AlarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    override fun schedule(alarm: ScheduledAlarm) {
        // Cancel any existing alarm with the same request code first
        cancel(alarm.requestCode)

        val triggerMillis = alarm.triggerTime.toEpochMilli()
        val alarmIntent = buildAlarmPendingIntent(alarm.requestCode, alarm.label)

        // showIntent opens the app when the user taps the alarm icon in the status bar
        val showIntent = buildShowPendingIntent()
        val alarmClockInfo = AlarmClockInfo(triggerMillis, showIntent)

        alarmManager.setAlarmClock(alarmClockInfo, alarmIntent)
    }

    override fun cancel(requestCode: Int) {
        val pendingIntent = buildAlarmPendingIntent(requestCode, "")
        alarmManager.cancel(pendingIntent)
    }

    override fun canScheduleExactAlarms(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    private fun buildAlarmPendingIntent(requestCode: Int, label: String): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_ALARM_FIRED
            putExtra(AlarmReceiver.EXTRA_REQUEST_CODE, requestCode)
            putExtra(AlarmReceiver.EXTRA_LABEL, label)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun buildShowPendingIntent(): PendingIntent {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: Intent()
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
