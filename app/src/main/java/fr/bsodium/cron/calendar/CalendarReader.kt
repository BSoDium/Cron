package fr.bsodium.cron.calendar

import android.content.ContentResolver
import android.content.ContentUris
import android.os.Bundle
import android.provider.CalendarContract
import android.util.Log
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * Reads calendar events from the device using [CalendarContract.Instances].
 *
 * The Instances table (not Events) correctly expands recurring events into
 * their individual occurrences. Uses the system's signed-in Google account —
 * no OAuth is required as long as READ_CALENDAR is granted.
 */
class CalendarReader(private val contentResolver: ContentResolver) {

    @Serializable
    data class Event(
        val id: Long,
        val title: String,
        val start: Instant,
        val end: Instant,
        val allDay: Boolean,
        val calendarId: Long,
        val location: String? = null,
    )

    fun readEvents(from: Instant, to: Instant): List<Event> {
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().apply {
            ContentUris.appendId(this, from.toEpochMilliseconds())
            ContentUris.appendId(this, to.toEpochMilliseconds())
        }.build()

        val cursor = try {
            contentResolver.query(
                uri,
                PROJECTION,
                null,
                null,
                "${CalendarContract.Instances.BEGIN} ASC",
            )
        } catch (_: SecurityException) {
            // Calendar permission was revoked at runtime — treat as empty.
            null
        } ?: return emptyList()

        val events = cursor.use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        Event(
                            id = c.getLong(COL_EVENT_ID),
                            title = c.getString(COL_TITLE) ?: "(No title)",
                            start = Instant.fromEpochMilliseconds(c.getLong(COL_BEGIN)),
                            end = Instant.fromEpochMilliseconds(c.getLong(COL_END)),
                            allDay = c.getInt(COL_ALL_DAY) == 1,
                            calendarId = c.getLong(COL_CALENDAR_ID),
                            location = c.getString(COL_LOCATION)?.ifBlank { null },
                        )
                    )
                }
            }
        }
        Log.i(TAG, "read ${events.size} events in [$from .. $to]: ${events.map { it.title }}")
        return events
    }

    private companion object {
        const val TAG = "CalendarReader"
        val PROJECTION = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.CALENDAR_ID,
            CalendarContract.Instances.EVENT_LOCATION,
        )
        const val COL_EVENT_ID = 0
        const val COL_TITLE = 1
        const val COL_BEGIN = 2
        const val COL_END = 3
        const val COL_ALL_DAY = 4
        const val COL_CALENDAR_ID = 5
        const val COL_LOCATION = 6
    }
}

/**
 * Best-effort nudge for the system to pull fresh calendar data before a planning read. Non-blocking;
 * the replan's own latency (location fetch + AI round-trip) usually covers the sync. A null account
 * syncs every account registered for the calendar authority.
 */
fun requestCalendarSync() {
    val extras = Bundle().apply {
        putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true)
        putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true)
    }
    ContentResolver.requestSync(null, CalendarContract.AUTHORITY, extras)
}
