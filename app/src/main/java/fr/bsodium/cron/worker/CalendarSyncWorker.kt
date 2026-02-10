package fr.bsodium.cron.worker

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import fr.bsodium.cron.engine.calendar.CalendarReaderImpl
import fr.bsodium.cron.engine.config.CronConfig
import fr.bsodium.cron.engine.orchestrator.CronOrchestrator
import fr.bsodium.cron.engine.scheduler.AlarmSchedulerImpl

/**
 * WorkManager worker that runs the Cron synchronization pass.
 *
 * Used for:
 * - Periodic background sync (every 3 hours, safety net)
 * - One-time sync triggered by [fr.bsodium.cron.receiver.CalendarChangeReceiver]
 */
class CalendarSyncWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    override fun doWork(): Result {
        val config = CronConfig.DEFAULT
        val calendarReader = CalendarReaderImpl(applicationContext.contentResolver, config)
        val alarmScheduler = AlarmSchedulerImpl(applicationContext)
        val orchestrator = CronOrchestrator(calendarReader, alarmScheduler, config)

        return try {
            orchestrator.synchronize()
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
