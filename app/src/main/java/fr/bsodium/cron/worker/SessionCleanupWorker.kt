package fr.bsodium.cron.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import fr.bsodium.cron.session.db.CronDatabase
import kotlinx.datetime.Clock
import kotlin.time.Duration.Companion.days

/**
 * Daily cleanup worker that deletes session rows older than [RETENTION].
 *
 * The matching `session_events` and `ai_messages` rows are removed via the
 * cascade declared on their foreign keys, so we only need to call
 * [fr.bsodium.cron.session.db.SessionDao.deleteOlderThan].
 *
 * Scheduled from [fr.bsodium.cron.CronApplication.onCreate] as periodic
 * unique work; subsequent app launches keep the existing schedule via
 * `ExistingPeriodicWorkPolicy.KEEP`.
 */
class SessionCleanupWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val cutoff = (Clock.System.now() - RETENTION).toEpochMilliseconds()
        return try {
            val removed = CronDatabase.get(applicationContext).sessionDao().deleteOlderThan(cutoff)
            if (removed > 0) Log.i(TAG, "Pruned $removed session rows older than $RETENTION")
            Result.success()
        } catch (t: Throwable) {
            Log.e(TAG, "Cleanup failed", t)
            Result.retry()
        }
    }

    companion object {
        const val NAME = "cron_session_cleanup"
        private const val TAG = "SessionCleanupWorker"
        val RETENTION = 30.days
    }
}
