package fr.bsodium.cron.calendar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RsvpStatusTest {

    @Test
    fun fromCode_maps_known_codes_to_the_right_status() {
        assertEquals(RsvpStatus.NotResponded, RsvpStatus.fromCode(0))
        assertEquals(RsvpStatus.Accepted, RsvpStatus.fromCode(1))
        assertEquals(RsvpStatus.Declined, RsvpStatus.fromCode(2))
        assertEquals(RsvpStatus.NotResponded, RsvpStatus.fromCode(3))
        assertEquals(RsvpStatus.Tentative, RsvpStatus.fromCode(4))
    }

    @Test
    fun fromCode_returns_null_for_unknown_code() {
        assertNull(RsvpStatus.fromCode(99))
    }

    @Test
    fun default_rsvp_statuses_exclude_declined() {
        assertEquals(
            setOf(RsvpStatus.Accepted, RsvpStatus.NotResponded, RsvpStatus.Tentative),
            DEFAULT_RSVP_STATUSES,
        )
        assertEquals(false, RsvpStatus.Declined in DEFAULT_RSVP_STATUSES)
    }
}
