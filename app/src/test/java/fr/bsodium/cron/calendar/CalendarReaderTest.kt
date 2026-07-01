package fr.bsodium.cron.calendar

import android.content.ContentResolver
import android.database.Cursor
import io.mockk.every
import io.mockk.mockk
import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CalendarReaderTest {

    private data class Row(
        val eventId: Long,
        val title: String?,
        val begin: Long,
        val end: Long,
        val allDay: Boolean,
        val calendarId: Long,
        val location: String?,
        val selfAttendeeStatus: Int,
    )

    private fun cursorOf(rows: List<Row>): Cursor {
        val cursor = mockk<Cursor>(relaxed = true)
        var index = -1
        every { cursor.moveToNext() } answers {
            index++
            index < rows.size
        }
        every { cursor.getLong(0) } answers { rows[index].eventId }
        every { cursor.getString(1) } answers { rows[index].title }
        every { cursor.getLong(2) } answers { rows[index].begin }
        every { cursor.getLong(3) } answers { rows[index].end }
        every { cursor.getInt(4) } answers { if (rows[index].allDay) 1 else 0 }
        every { cursor.getLong(5) } answers { rows[index].calendarId }
        every { cursor.getString(6) } answers { rows[index].location }
        every { cursor.getInt(7) } answers { rows[index].selfAttendeeStatus }
        return cursor
    }

    private val from = Instant.parse("2026-05-22T00:00:00Z")
    private val to = Instant.parse("2026-05-22T18:00:00Z")

    @Test
    fun reads_events_and_maps_all_fields() {
        val row = Row(
            eventId = 42L,
            title = "Standup",
            begin = from.toEpochMilliseconds() + 1000,
            end = from.toEpochMilliseconds() + 2000,
            allDay = false,
            calendarId = 7L,
            location = "Office",
            selfAttendeeStatus = 1,
        )
        val resolver = mockk<ContentResolver>()
        every { resolver.query(any(), any(), any(), any(), any()) } returns cursorOf(listOf(row))

        val events = CalendarReader(resolver).readEvents(from, to)
        assertEquals(1, events.size)
        val event = events.single()
        assertEquals(42L, event.id)
        assertEquals("Standup", event.title)
        assertEquals("Office", event.location)
        assertEquals(7L, event.calendarId)
        assertEquals(false, event.allDay)
        assertEquals(1, event.selfAttendeeStatus)
    }

    @Test
    fun blank_location_is_normalized_to_null() {
        val row = Row(
            eventId = 1L,
            title = "No location",
            begin = from.toEpochMilliseconds(),
            end = from.toEpochMilliseconds() + 1000,
            allDay = false,
            calendarId = 1L,
            location = "   ",
            selfAttendeeStatus = 1,
        )
        val resolver = mockk<ContentResolver>()
        every { resolver.query(any(), any(), any(), any(), any()) } returns cursorOf(listOf(row))

        val events = CalendarReader(resolver).readEvents(from, to)
        assertEquals(null, events.single().location)
    }

    @Test
    fun missing_title_defaults_to_placeholder() {
        val row = Row(
            eventId = 1L,
            title = null,
            begin = from.toEpochMilliseconds(),
            end = from.toEpochMilliseconds() + 1000,
            allDay = false,
            calendarId = 1L,
            location = null,
            selfAttendeeStatus = 1,
        )
        val resolver = mockk<ContentResolver>()
        every { resolver.query(any(), any(), any(), any(), any()) } returns cursorOf(listOf(row))

        val events = CalendarReader(resolver).readEvents(from, to)
        assertEquals("(No title)", events.single().title)
    }

    @Test
    fun events_outside_allowed_rsvp_statuses_are_filtered_out() {
        val accepted = Row(1L, "Accepted", from.toEpochMilliseconds(), from.toEpochMilliseconds() + 1000, false, 1L, null, 1)
        val declined = Row(2L, "Declined", from.toEpochMilliseconds(), from.toEpochMilliseconds() + 1000, false, 1L, null, 2)
        val resolver = mockk<ContentResolver>()
        every { resolver.query(any(), any(), any(), any(), any()) } returns cursorOf(listOf(accepted, declined))

        val events = CalendarReader(resolver).readEvents(from, to, allowedRsvpStatuses = setOf(RsvpStatus.Accepted))
        assertEquals(listOf("Accepted"), events.map { it.title })
    }

    @Test
    fun null_cursor_from_query_yields_empty_list() {
        val resolver = mockk<ContentResolver>()
        every { resolver.query(any(), any(), any(), any(), any()) } returns null

        assertTrue(CalendarReader(resolver).readEvents(from, to).isEmpty())
    }

    @Test
    fun security_exception_from_query_yields_empty_list() {
        val resolver = mockk<ContentResolver>()
        every { resolver.query(any(), any(), any(), any(), any()) } throws SecurityException("permission revoked")

        assertTrue(CalendarReader(resolver).readEvents(from, to).isEmpty())
    }
}
