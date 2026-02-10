package fr.bsodium.cron.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import fr.bsodium.cron.engine.calendar.CalendarReaderImpl
import fr.bsodium.cron.engine.config.CronConfig
import fr.bsodium.cron.engine.orchestrator.CronOrchestrator
import fr.bsodium.cron.engine.scheduler.AlarmSchedulerImpl

/**
 * Re-schedules alarms after device reboot.
 *
 * AlarmManager alarms are cleared on reboot, so this receiver
 * re-runs the scheduling algorithm to re-establish them immediately.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val config = CronConfig.DEFAULT
        val calendarReader = CalendarReaderImpl(context.contentResolver, config)
        val alarmScheduler = AlarmSchedulerImpl(context)
        val orchestrator = CronOrchestrator(calendarReader, alarmScheduler, config)

        orchestrator.synchronize()
    }
}
