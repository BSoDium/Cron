package fr.bsodium.cron.ai

import fr.bsodium.cron.ai.wire.ContentBlock
import fr.bsodium.cron.session.model.TriggerType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The blocks of the actively-streaming turn so far; rebuilt into the home thread for live rendering. */
data class StreamingTurn(
    val sessionId: String,
    val turnIndex: Int,
    val blocks: List<ContentBlock>,
    val startedAtMs: Long,
    /** The trigger this turn was seeded with, so the UI labels it correctly before the real event lands. */
    val trigger: TriggerType? = null,
    /** True when produced by the FakeAnthropicClient (debug mock mode). */
    val isMocked: Boolean = false,
)

/**
 * Process-wide hand-off of the in-flight turn from the [TurnRunner] (running in the WorkManager
 * worker) to the home UI. Worker and UI share the default process, so a singleton [StateFlow] is a
 * valid channel — mirrors [fr.bsodium.cron.sensors.DebugSensorEventSink].
 *
 * Partials are ephemeral by design: only complete messages are persisted to Room, so process death
 * drops the in-memory partial while the DB-backed settled state stays the source of truth.
 */
object StreamingTurnStore {
    private val _active = MutableStateFlow<StreamingTurn?>(null)
    val active: StateFlow<StreamingTurn?> = _active.asStateFlow()

    fun update(turn: StreamingTurn) {
        _active.value = turn
    }

    /**
     * Seeds an empty placeholder for an upcoming turn so the UI shows it the instant a replan is
     * triggered (before the worker spins up). The worker's first [update] for the same
     * [sessionId]/[turnIndex] then replaces it seamlessly — one iteration, no add/remove flicker.
     */
    fun seedPending(sessionId: String, turnIndex: Int, startedAtMs: Long, trigger: TriggerType? = null) =
        update(StreamingTurn(sessionId, turnIndex, blocks = emptyList(), startedAtMs = startedAtMs, trigger = trigger))

    /**
     * Clears the partial for [sessionId]/[turnIndex] — but only if it's still the active one, so a
     * REPLACE-enqueued worker that already started a newer turn can't be cleared by the old one's
     * `finally`.
     */
    fun clear(sessionId: String, turnIndex: Int) {
        val current = _active.value
        if (current?.sessionId == sessionId && current.turnIndex == turnIndex) {
            _active.value = null
        }
    }
}
