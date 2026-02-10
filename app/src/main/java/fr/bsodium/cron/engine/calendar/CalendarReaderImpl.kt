package fr.bsodium.cron.engine.calendar

import android.content.ContentResolver
import android.content.ContentUris
import android.database.Cursor
import android.provider.CalendarContract
import fr.bsodium.cron.engine.config.CronConfig
import fr.bsodium.cron.engine.model.CalendarEvent
import java.time.Instant

/**
 * Reads calendar events from the device using [CalendarContract.Instances].
 *
 * Uses the Instances table (not Events) to correctly expand recurring events
 * into their individual occurrences.
 */
class CalendarReaderImpl(
    private val contentResolver: ContentResolver,
    private val config: CronConfig = CronConfig.DEFAULT
) : CalendarReader {

    companion object {
        private val PROJECTION = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.CALENDAR_ID,
            CalendarContract.Instances.EVENT_LOCATION
        )

        private const val COL_EVENT_ID = 0
        private const val COL_TITLE = 1
        private const val COL_BEGIN = 2
        private const val COL_END = 3
        private const val COL_ALL_DAY = 4
        private const val COL_CALENDAR_ID = 5
        private const val COL_LOCATION = 6
    }

    override fun readEvents(from: Instant, to: Instant): List<CalendarEvent> {
        val beginMillis = from.toEpochMilli()
        val endMillis = to.toEpochMilli()

        // Build the Instances query URI with the time range
        val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(builder, beginMillis)
        ContentUris.appendId(builder, endMillis)
        val uri = builder.build()

        // Build selection clause
        val selectionParts = mutableListOf<String>()
        val selectionArgs = mutableListOf<String>()

        if (config.skipAllDayEvents) {
            selectionParts.add("${CalendarContract.Instances.ALL_DAY} = ?")
            selectionArgs.add("0")
        }

        if (config.calendarIds.isNotEmpty()) {
            val placeholders = config.calendarIds.joinToString(",") { "?" }
            selectionParts.add("${CalendarContract.Instances.CALENDAR_ID} IN ($placeholders)")
            selectionArgs.addAll(config.calendarIds.map { it.toString() })
        }

        val selection = selectionParts.joinToString(" AND ").ifEmpty { null }
        val args = selectionArgs.toTypedArray().ifEmpty { null }
        val sortOrder = "${CalendarContract.Instances.BEGIN} ASC"

        val cursor: Cursor? = try {
            contentResolver.query(uri, PROJECTION, selection, args, sortOrder)
        } catch (_: SecurityException) {
            // Calendar permission was revoked at runtime
            null
        }

        val events = mutableListOf<CalendarEvent>()

        cursor?.use {
            while (it.moveToNext()) {
                val event = CalendarEvent(
                    id = it.getLong(COL_EVENT_ID),
                    title = it.getString(COL_TITLE) ?: "(No title)",
                    startTime = Instant.ofEpochMilli(it.getLong(COL_BEGIN)),
                    endTime = Instant.ofEpochMilli(it.getLong(COL_END)),
                    isAllDay = it.getInt(COL_ALL_DAY) == 1,
                    calendarId = it.getLong(COL_CALENDAR_ID),
                    location = it.getString(COL_LOCATION)
                )
                events.add(event)
            }
        }

        return events
    }
}
