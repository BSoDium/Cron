package fr.bsodium.cron.sensors

import kotlinx.datetime.Instant

/** A raw, timestamped sensor observation, logged unconditionally -- unlike [SensorEventSink], which
 *  only carries the subset of events the FSM acts on. This is the source hindsight relabeling reads
 *  from once it exists; see docs/sleep-detection-architecture.md §5 (ObservationLog). */
data class RawObservation(
    val type: String,
    val timestamp: Instant,
    val payloadJson: String = "{}",
)

interface RawObservationSink {
    suspend fun log(observation: RawObservation)
}

/** Default for monitors that don't need raw logging (most tests). */
object NoOpObservationSink : RawObservationSink {
    override suspend fun log(observation: RawObservation) = Unit
}
