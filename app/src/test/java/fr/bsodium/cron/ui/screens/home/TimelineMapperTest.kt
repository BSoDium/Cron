package fr.bsodium.cron.ui.screens.home

import fr.bsodium.cron.session.model.TriggerType
import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineMapperTest {

    private fun events(count: Int): List<TimelineItem> = (0 until count).map { i ->
        TimelineItem.Event(
            timestamp = Instant.fromEpochMilliseconds(i.toLong()),
            trigger = TriggerType.AlarmDismissed,
            label = "Event $i",
            detail = null,
        )
    }

    @Test
    fun under_cap_is_untouched_and_not_truncated() {
        val result = capTimeline(events(10), cap = 24)
        assertEquals(10, result.items.size)
        assertFalse(result.truncated)
    }

    @Test
    fun exactly_at_cap_is_not_truncated() {
        val result = capTimeline(events(24), cap = 24)
        assertEquals(24, result.items.size)
        assertFalse(result.truncated)
    }

    @Test
    fun over_cap_keeps_the_leading_slice_and_flags_truncated() {
        val items = events(30)
        val result = capTimeline(items, cap = 24)
        assertEquals(24, result.items.size)
        assertTrue(result.truncated)
        assertEquals(items.take(24), result.items)
    }
}
