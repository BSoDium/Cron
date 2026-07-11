package fr.bsodium.cron.calendar

import android.content.ContentResolver
import fr.bsodium.cron.session.model.SleepSession
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlin.time.Duration.Companion.hours

/**
 * Computes a lightweight signature for the first non-all-day event on a
 * session's morning date and compares it against the cached value in Room.
 *
 * The signature encodes `eventId|startMs|location` — if any of these fields
 * change, we treat the first anchor event as changed and trigger an AI replan.
 * All-day events are excluded because they are markers, not appointments.
 */
class CalendarChangeAnalyzer(
    private val contentResolver: ContentResolver,
    private val allowedRsvpStatuses: Set<RsvpStatus> = RsvpStatus.entries.toSet(),
) {

    data class Result(
        val firstEventChanged: Boolean,
        val newSig: String?,
        /** The first event's own title, threaded out so the timeline can build a human-readable
         *  summary of what changed instead of just "your first event changed". */
        val firstEventTitle: String?,
    )

    fun analyze(session: SleepSession): Result {
        val tz = TimeZone.of(session.timezone)
        val first = firstMorningEvent(session, tz)
        val currentSig = first?.let { "${it.id}|${it.start.toEpochMilliseconds()}|${it.location.orEmpty()}|${it.selfAttendeeStatus}" }
        return Result(
            firstEventChanged = currentSig != session.cachedFirstEventSig,
            newSig = currentSig,
            firstEventTitle = first?.title,
        )
    }

    private fun firstMorningEvent(session: SleepSession, timezone: TimeZone): CalendarReader.Event? {
        val dayStart = session.date.atStartOfDayIn(timezone)
        val dayEnd = dayStart + MORNING_WINDOW
        val events = CalendarReader(contentResolver).readEvents(dayStart, dayEnd, allowedRsvpStatuses = allowedRsvpStatuses)
        return events.firstOrNull { !it.allDay }
    }

    companion object {
        private val MORNING_WINDOW = 18.hours
    }
}
