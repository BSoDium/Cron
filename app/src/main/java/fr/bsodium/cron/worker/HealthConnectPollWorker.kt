package fr.bsodium.cron.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import fr.bsodium.cron.sensors.DebugSensorEventSink
import fr.bsodium.cron.sensors.healthconnect.SleepStageReader
import fr.bsodium.cron.session.SessionFsm
import fr.bsodium.cron.session.SessionRepository
import fr.bsodium.cron.settings.PollCheckpointStore
import kotlinx.datetime.Clock
import kotlin.time.Duration.Companion.hours

/**
 * Periodic worker that polls Health Connect for new sleep stage records
 * and delivers them to [SessionFsm].
 *
 * If no active session exists, exits immediately without reading Health Connect.
 */
class HealthConnectPollWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val reader = SleepStageReader(applicationContext)
        if (reader.availability() != SleepStageReader.Availability.Available) {
            return Result.success()
        }

        val repository = SessionRepository(applicationContext)
        val session = repository.findCurrent()
        if (session == null) {
            Log.d(TAG, "No active session — skipping HC poll")
            return Result.success()
        }

        val checkpoints = PollCheckpointStore(applicationContext)
        val since = checkpoints.lastHealthConnectPoll() ?: (Clock.System.now() - 12.hours)

        val fsm = SessionFsm(applicationContext, repository)
        val latest = reader.readSince(since) { event ->
            DebugSensorEventSink.emit(event) // keep debug card working
            try {
                fsm.onEvent(event)
            } catch (t: Throwable) {
                Log.e(TAG, "FSM error on HC stage update", t)
            }
        }

        latest?.let { checkpoints.setLastHealthConnectPoll(it) }
        return Result.success()
    }

    companion object {
        const val NAME = "cron_health_connect_poll"
        private const val TAG = "HealthConnectPollWorker"
    }
}
