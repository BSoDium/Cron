package fr.bsodium.cron.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import fr.bsodium.cron.worker.CalendarChangeWorker
import java.util.concurrent.TimeUnit

/**
 * Responds to calendar provider changes and triggers a debounced analysis.
 *
 * Calendar change events can fire in rapid bursts (e.g., syncing a batch of
 * events). The 2-second delay + REPLACE policy means only the last change
 * in a burst triggers [CalendarChangeWorker], which checks whether the first
 * anchor event actually changed before touching the session or AI.
 */
class CalendarChangeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val request = OneTimeWorkRequestBuilder<CalendarChangeWorker>()
            .setInitialDelay(2, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            CalendarChangeWorker.NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }
}
