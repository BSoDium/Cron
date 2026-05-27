package fr.bsodium.cron.session.model

import kotlinx.datetime.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO

/**
 * One contiguous stage segment from Health Connect, used to render the
 * sleep timeline on the home hero card.
 */
data class SleepSegment(
    val stage: SleepStage,
    val start: Instant,
    val end: Instant,
) {
    val duration: Duration get() = end - start
}

/**
 * Chronologically ordered sleep stage segments for the session. Returns an
 * empty list if no Health Connect stage data has arrived yet.
 */
fun SleepSession.sleepSegments(): List<SleepSegment> = events
    .mapNotNull { ev -> (ev.data as? EventData.HcStageUpdate)?.let { it to ev } }
    .map { (data, _) ->
        SleepSegment(
            stage = data.stage,
            start = data.recordStart,
            end = data.recordEnd,
        )
    }
    .sortedBy { it.start }

/**
 * Total time in bed (clock-time span from earliest to latest sleep segment).
 * Returns [Duration.ZERO] when no stage data is available.
 */
fun SleepSession.timeInBed(): Duration {
    val segments = sleepSegments()
    if (segments.isEmpty()) return ZERO
    val start = segments.first().start
    val end = segments.last().end
    return end - start
}
