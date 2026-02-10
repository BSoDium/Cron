package fr.bsodium.cron.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import fr.bsodium.cron.worker.CalendarSyncWorker

/**
 * Responds to calendar provider changes (event created, modified, deleted).
 *
 * Instead of running the orchestrator directly (which could block the
 * broadcast receiver's time limit), this enqueues an expedited one-time
 * [CalendarSyncWorker] to handle the re-sync.
 */
class CalendarChangeReceiver : BroadcastReceiver() {

    companion object {
        private const val WORK_NAME = "calendar_change_sync"
    }

    override fun onReceive(context: Context, intent: Intent) {
        // Enqueue a one-time sync, replacing any pending one (debounce)
        val syncRequest = OneTimeWorkRequestBuilder<CalendarSyncWorker>().build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            syncRequest
        )
    }
}
