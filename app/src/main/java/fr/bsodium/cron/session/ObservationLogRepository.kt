package fr.bsodium.cron.session

import android.content.Context
import fr.bsodium.cron.sensors.RawObservation
import fr.bsodium.cron.sensors.RawObservationSink
import fr.bsodium.cron.session.db.CronDatabase
import fr.bsodium.cron.session.db.ObservationEntity

/** Room-backed [RawObservationSink]: attaches each observation to whatever session is currently
 *  active, and drops it silently once no session is running (nothing to attach it to). */
class ObservationLogRepository(private val context: Context) : RawObservationSink {

    private val db get() = CronDatabase.get(context)

    override suspend fun log(observation: RawObservation) {
        val sessionId = db.sessionDao().findCurrent()?.id ?: return
        db.observationDao().insert(
            ObservationEntity(
                sessionId = sessionId,
                type = observation.type,
                timestamp = observation.timestamp.toEpochMilliseconds(),
                payloadJson = observation.payloadJson,
            )
        )
    }
}
