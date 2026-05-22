package fr.bsodium.cron.sensors

import fr.bsodium.cron.session.model.SessionEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Where sensors send their observations.
 *
 * Phase 4 implements a flow-based sink that surfaces events to the debug
 * UI. Phase 5 layers a SessionRepository-backed sink on top so the FSM
 * gets durable, transactional event handling.
 */
interface SensorEventSink {
    suspend fun emit(event: SessionEvent)
}

/**
 * Process-wide sink used by sensors during Phase 4. The service connects
 * each monitor to this singleton; the debug UI observes [events] to
 * visualise sensor activity in real time.
 */
object DebugSensorEventSink : SensorEventSink {
    private val _events = MutableSharedFlow<SessionEvent>(
        replay = 32,
        extraBufferCapacity = 64,
    )
    val events: SharedFlow<SessionEvent> = _events.asSharedFlow()

    override suspend fun emit(event: SessionEvent) {
        _events.emit(event)
    }
}
