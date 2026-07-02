package fr.bsodium.cron.calendar

import android.content.ContentResolver
import android.database.Cursor
import fr.bsodium.cron.testutil.Fixtures
import io.mockk.every
import io.mockk.mockk
import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CalendarChangeAnalyzerTest {

    private data class Row(
        val eventId: Long,
        val title: String,
        val begin: Long,
        val end: Long,
        val allDay: Boolean,
        val calendarId: Long,
        val location: String?,
        val selfAttendeeStatus: Int,
    )

    private fun newCursor(rows: List<Row>): Cursor {
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

    private fun contentResolver(rows: List<Row>): ContentResolver {
        val resolver = mockk<ContentResolver>()
        every { resolver.query(any(), any(), any(), any(), any()) } answers { newCursor(rows) }
        return resolver
    }

    private val morningStart = Instant.parse("2026-05-22T04:00:00Z") // ~06:00 Paris

    @Test
    fun unchanged_signature_reports_no_change() {
        val row = Row(
            eventId = 1L,
            title = "Standup",
            begin = morningStart.toEpochMilliseconds(),
            end = morningStart.toEpochMilliseconds() + 60_000,
            allDay = false,
            calendarId = 10L,
            location = "Office",
            selfAttendeeStatus = 1,
        )
        val resolver = contentResolver(listOf(row))
        val analyzer = CalendarChangeAnalyzer(resolver)

        val first = analyzer.analyze(Fixtures.session())
        assertTrue(first.firstEventChanged)
        assertEquals("1|${row.begin}|Office|1", first.newSig)

        val session2 = Fixtures.session().copy(cachedFirstEventSig = first.newSig)
        val second = analyzer.analyze(session2)
        assertFalse(second.firstEventChanged)
        assertEquals(first.newSig, second.newSig)
    }

    @Test
    fun changed_start_time_is_detected() {
        val row = Row(
            eventId = 1L,
            title = "Standup",
            begin = morningStart.toEpochMilliseconds(),
            end = morningStart.toEpochMilliseconds() + 60_000,
            allDay = false,
            calendarId = 10L,
            location = "Office",
            selfAttendeeStatus = 1,
        )
        val cachedSig = "1|${row.begin - 3_600_000}|Office|1"
        val session = Fixtures.session().copy(cachedFirstEventSig = cachedSig)

        val result = CalendarChangeAnalyzer(contentResolver(listOf(row))).analyze(session)
        assertTrue(result.firstEventChanged)
    }

    @Test
    fun all_day_event_is_excluded_and_first_timed_event_is_used() {
        val allDay = Row(
            eventId = 1L,
            title = "Vacation",
            begin = morningStart.toEpochMilliseconds(),
            end = morningStart.toEpochMilliseconds() + 86_400_000,
            allDay = true,
            calendarId = 10L,
            location = null,
            selfAttendeeStatus = 1,
        )
        val timed = Row(
            eventId = 2L,
            title = "Standup",
            begin = morningStart.toEpochMilliseconds() + 3_600_000,
            end = morningStart.toEpochMilliseconds() + 3_660_000,
            allDay = false,
            calendarId = 10L,
            location = "Office",
            selfAttendeeStatus = 1,
        )
        val resolver = contentResolver(listOf(allDay, timed))
        val result = CalendarChangeAnalyzer(resolver).analyze(Fixtures.session())
        assertEquals("2|${timed.begin}|Office|1", result.newSig)
    }

    @Test
    fun no_events_yields_null_signature() {
        val result = CalendarChangeAnalyzer(contentResolver(emptyList())).analyze(Fixtures.session())
        assertFalse(result.firstEventChanged)
        assertNull(result.newSig)
    }

    @Test
    fun only_all_day_events_yields_null_signature() {
        val allDay = Row(
            eventId = 1L,
            title = "Vacation",
            begin = morningStart.toEpochMilliseconds(),
            end = morningStart.toEpochMilliseconds() + 86_400_000,
            allDay = true,
            calendarId = 10L,
            location = null,
            selfAttendeeStatus = 1,
        )
        val result = CalendarChangeAnalyzer(contentResolver(listOf(allDay))).analyze(Fixtures.session())
        assertNull(result.newSig)
    }

    @Test
    fun declined_event_excluded_when_rsvp_filter_disallows_it() {
        val declined = Row(
            eventId = 1L,
            title = "Standup",
            begin = morningStart.toEpochMilliseconds(),
            end = morningStart.toEpochMilliseconds() + 60_000,
            allDay = false,
            calendarId = 10L,
            location = "Office",
            selfAttendeeStatus = 2, // Declined code
        )
        val analyzer = CalendarChangeAnalyzer(
            contentResolver(listOf(declined)),
            allowedRsvpStatuses = setOf(RsvpStatus.Accepted),
        )
        val result = analyzer.analyze(Fixtures.session())
        assertNull(result.newSig)
        assertFalse(result.firstEventChanged)
    }

    @Test
    fun declined_event_included_when_rsvp_filter_allows_declined() {
        val declined = Row(
            eventId = 1L,
            title = "Standup",
            begin = morningStart.toEpochMilliseconds(),
            end = morningStart.toEpochMilliseconds() + 60_000,
            allDay = false,
            calendarId = 10L,
            location = "Office",
            selfAttendeeStatus = 2,
        )
        val analyzer = CalendarChangeAnalyzer(
            contentResolver(listOf(declined)),
            allowedRsvpStatuses = setOf(RsvpStatus.Declined),
        )
        val result = analyzer.analyze(Fixtures.session())
        assertEquals("1|${declined.begin}|Office|2", result.newSig)
    }
}
