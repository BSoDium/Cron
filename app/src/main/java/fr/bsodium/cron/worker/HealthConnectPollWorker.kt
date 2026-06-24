package fr.bsodium.cron.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import fr.bsodium.cron.sensors.DebugSensorEventSink
import fr.bsodium.cron.sensors.healthconnect.SleepStageReader
import fr.bsodium.cron.session.SessionFsm
import fr.bsodium.cron.session.SessionRepository
import fr.bsodium.cron.session.model.ActionType
import fr.bsodium.cron.session.model.EventData
import fr.bsodium.cron.session.model.SessionEvent
import fr.bsodium.cron.session.model.SessionStatus
import fr.bsodium.cron.session.model.SignalConfidence
import fr.bsodium.cron.session.model.SleepSession
import fr.bsodium.cron.session.model.SleepStage
import fr.bsodium.cron.session.model.TriggerType
import fr.bsodium.cron.settings.PollCheckpointStore
import fr.bsodium.cron.settings.SettingsRepository
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * Periodic worker that polls Health Connect for new sleep stage records
 * and delivers them to [SessionFsm].
 *
 * If no active session exists, exits immediately without reading Health Connect.
 *
 * On top of forwarding raw [EventData.HcStageUpdate] events, this worker also
 * detects "wake window opportunities" — moments when the user is in a light or
 * REM stage within the last 30 minutes before the AI alarm. When that happens,
 * a single [EventData.WakeWindowOpportunity] is emitted per session so the AI
 * can nudge the alarm to ring sooner and avoid waking the user out of deep sleep.
 */
class HealthConnectPollWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (!SettingsRepository(applicationContext).autoAlarmsEnabledNow()) {
            Log.d(TAG, "Auto-plan disabled — skipping HC poll")
            return Result.success()
        }
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
        var wakeOppEmitted = session.events.any {
            it.trigger == TriggerType.WakeWindowOpportunity
        }

        val latest = reader.readSince(since) { event ->
            DebugSensorEventSink.emit(event)
            try {
                fsm.onEvent(event)
                if (!wakeOppEmitted) {
                    detectWakeOpportunity(event, session)?.let { opp ->
                        DebugSensorEventSink.emit(opp)
                        fsm.onEvent(opp)
                        wakeOppEmitted = true
                        Log.i(TAG, "Emitted wake_window_opportunity for session ${session.id}")
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "FSM error on HC stage update", t)
            }
        }

        latest?.let { checkpoints.setLastHealthConnectPoll(it) }
        return Result.success()
    }

    private fun detectWakeOpportunity(
        event: SessionEvent,
        session: SleepSession,
    ): SessionEvent? {
        val stageUpdate = event.data as? EventData.HcStageUpdate ?: return null
        if (stageUpdate.stage !in WAKE_FRIENDLY_STAGES) return null
        if (stageUpdate.confidence != SignalConfidence.High) return null
        if (session.status !in MONITORING_STATES) return null

        val instruction = session.currentInstruction
        if (instruction.action != ActionType.SetAlarm) return null
        val alarmTime = instruction.alarmTime ?: return null

        val tz = TimeZone.of(session.timezone)
        val aiAlarmInstant = session.date.atTime(alarmTime).toInstant(tz)
        val windowStart = aiAlarmInstant - 30.minutes
        val now = event.timestamp
        if (now < windowStart || now >= aiAlarmInstant) return null

        return SessionEvent(
            trigger = TriggerType.WakeWindowOpportunity,
            timestamp = now,
            data = EventData.WakeWindowOpportunity(
                currentStage = stageUpdate.stage,
                windowStart = windowStart.toLocalDateTime(tz).time,
                windowEnd = alarmTime,
            ),
        )
    }

    companion object {
        const val NAME = "cron_health_connect_poll"
        private const val TAG = "HealthConnectPollWorker"
        private val WAKE_FRIENDLY_STAGES = setOf(SleepStage.Light, SleepStage.Rem)
        private val MONITORING_STATES =
            setOf(SessionStatus.Monitoring, SessionStatus.ReMonitoring)
    }
}
