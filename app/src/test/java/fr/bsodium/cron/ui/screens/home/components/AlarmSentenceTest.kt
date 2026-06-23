package fr.bsodium.cron.ui.screens.home.components

import org.junit.Assert.assertEquals
import org.junit.Test

class AlarmSentenceTest {

    private val upcoming = AlarmTiming.Upcoming(HoursMinutes(hours = 1, minutes = 0))

    @Test
    fun past_rewrites_future_tense_to_past() {
        assertEquals(
            "Today, you woke up at",
            alarmSentenceForTiming("Today, you'll wake up at", AlarmTiming.Past),
        )
        assertEquals(
            "Tuesday, you woke up at",
            alarmSentenceForTiming("Tuesday, you'll wake up at", AlarmTiming.Past),
        )
    }

    @Test
    fun upcoming_and_none_are_unchanged() {
        assertEquals(
            "Today, you'll wake up at",
            alarmSentenceForTiming("Today, you'll wake up at", upcoming),
        )
        assertEquals(
            "Today, you'll wake up at",
            alarmSentenceForTiming("Today, you'll wake up at", AlarmTiming.None),
        )
    }

    @Test
    fun past_leaves_comma_less_labels_alone() {
        // e.g. the auto-plan-disabled string — no day-part comma, so no rewrite.
        assertEquals(
            "Your alarm is disabled",
            alarmSentenceForTiming("Your alarm is disabled", AlarmTiming.Past),
        )
    }
}
