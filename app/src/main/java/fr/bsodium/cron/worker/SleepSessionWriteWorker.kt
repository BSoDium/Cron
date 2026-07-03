package fr.bsodium.cron.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import fr.bsodium.cron.sensors.healthconnect.SleepSessionWriter
import fr.bsodium.cron.session.SessionRepository
import fr.bsodium.cron.session.model.detectedSleepWindow
import fr.bsodium.cron.settings.SettingsRepository
import kotlinx.datetime.TimeZone

/**
 * One-off worker that writes a just-completed session's [detectedSleepWindow] to Health Connect.
 * Enqueued by [SessionRepository.triggerSleepSessionWrite] when a session reaches
 * [fr.bsodium.cron.session.model.SessionStatus.Complete]. Every precondition failure is a graceful
 * skip (log + [Result.success]) — writing to Health Connect is a nice-to-have, never worth crashing
 * or retrying over.
 */
class SleepSessionWriteWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val sessionId = inputData.getString(KEY_SESSION_ID) ?: return Result.failure()

        if (!SettingsRepository(applicationContext).saveSleepToHealthConnectNow()) {
            Log.d(TAG, "Health Connect sleep write disabled — skipping")
            return Result.success()
        }

        val session = SessionRepository(applicationContext).findById(sessionId)
        if (session == null) {
            Log.w(TAG, "Session $sessionId not found — skipping HC write")
            return Result.success()
        }

        val window = session.detectedSleepWindow()
        if (window == null) {
            Log.d(TAG, "Session $sessionId has no onset/wake pair — skipping HC write")
            return Result.success()
        }

        val writer = SleepSessionWriter(applicationContext)
        if (!writer.hasWritePermission()) {
            Log.d(TAG, "Health Connect write permission not granted — skipping")
            return Result.success()
        }

        val wrote = writer.write(
            start = window.start,
            end = window.end,
            timezone = TimeZone.of(session.timezone),
            clientRecordId = sessionId,
        )
        Log.i(TAG, "Session $sessionId Health Connect write ${if (wrote) "succeeded" else "failed"}")
        return Result.success()
    }

    companion object {
        const val NAME_PREFIX = "cron_hc_sleep_write_"
        const val KEY_SESSION_ID = "session_id"
        private const val TAG = "SleepSessionWriteWorker"
    }
}
