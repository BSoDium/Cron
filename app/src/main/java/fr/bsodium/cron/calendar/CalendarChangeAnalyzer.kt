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
class CalendarChangeAnalyzer(private val contentResolver: ContentResolver) {

    data class Result(
        val firstEventChanged: Boolean,
        val newSig: String?,
    )

    fun analyze(session: SleepSession): Result {
        val tz = TimeZone.of(session.timezone)
        val currentSig = computeFirstEventSig(session, tz)
        return Result(
            firstEventChanged = currentSig != session.cachedFirstEventSig,
            newSig = currentSig,
        )
    }

    private fun computeFirstEventSig(session: SleepSession, timezone: TimeZone): String? {
        val dayStart = session.date.atStartOfDayIn(timezone)
        val dayEnd = dayStart + MORNING_WINDOW
        val events = CalendarReader(contentResolver).readEvents(dayStart, dayEnd)
        val first = events.firstOrNull { !it.allDay } ?: return null
        return "${first.id}|${first.start.toEpochMilliseconds()}|${first.location.orEmpty()}"
    }

    companion object {
        private val MORNING_WINDOW = 18.hours
    }
}
