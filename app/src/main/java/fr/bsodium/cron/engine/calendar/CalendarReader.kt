package fr.bsodium.cron.engine.calendar

import fr.bsodium.cron.engine.model.CalendarEvent
import java.time.Instant

/**
 * Abstraction for reading calendar events.
 * Implementations may use Android's [android.provider.CalendarContract]
 * or a test double.
 */
interface CalendarReader {

    /**
     * Returns events whose start time falls within [from, to),
     * sorted by start time ascending.
     */
    fun readEvents(from: Instant, to: Instant): List<CalendarEvent>
}
