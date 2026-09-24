package fr.bsodium.cron.session

import android.content.Context
import android.util.Log
import fr.bsodium.cron.sensors.RawObservation
import fr.bsodium.cron.sensors.RawObservationSink
import fr.bsodium.cron.session.db.CronDatabase
import fr.bsodium.cron.session.db.ObservationEntity
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** Room-backed [RawObservationSink]: attaches each observation to whatever session is currently
 *  active, and drops it silently once no session is running (nothing to attach it to).
 *
 *  The resolved session id is cached after the first lookup for the lifetime of this instance
 *  (one per [fr.bsodium.cron.service.SleepSessionService]), since it doesn't change while a
 *  session is being tracked — call [invalidate] when a new evening plan may have bootstrapped or
 *  superseded a session, so the next observation re-resolves instead of reusing a stale id. */
class ObservationLogRepository(private val context: Context) : RawObservationSink {

    private val db get() = CronDatabase.get(context)

    @Volatile
    private var cachedSessionId: String? = null

    fun invalidate() {
        cachedSessionId = null
    }

    override suspend fun log(observation: RawObservation) {
        runCatching {
            // NonCancellable: this is a best-effort background write, but a write already in flight
            // when the service tears down (SleepSessionService.onDestroy cancels this coroutine's
            // scope) must still land -- that's the one moment "logged unconditionally" matters most.
            withContext(NonCancellable) {
                val sessionId = cachedSessionId
                    ?: resolveSessionId()?.also { cachedSessionId = it }
                    ?: return@withContext
                db.observationDao().insert(
                    ObservationEntity(
                        sessionId = sessionId,
                        type = observation.type,
                        timestamp = observation.timestamp.toEpochMilliseconds(),
                        payloadJson = observation.payloadJson,
                    )
                )
            }
        }.onFailure { Log.w(TAG, "Failed to log observation '${observation.type}'", it) }
    }

    /** Serialized against [SessionFsm]'s own lock so this can't observe a session mid-supersede or
     *  mid-bootstrap (#153) and attach an observation to the wrong (or no) session. */
    private suspend fun resolveSessionId(): String? =
        SessionFsm.withSessionLock { db.sessionDao().findCurrent()?.id }

    private companion object {
        const val TAG = "ObservationLogRepo"
    }
}
