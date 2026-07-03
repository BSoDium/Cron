package fr.bsodium.cron.session.model

import fr.bsodium.cron.testutil.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SleepWindowTest {

    private fun onset(at: String) = SessionEvent(trigger = TriggerType.SleepOnset, timestamp = Fixtures.at(at), data = EventData.SleepOnset(screenOffSince = Fixtures.at(at), rearm = false))
    private fun outOfBed(at: String) = SessionEvent(trigger = TriggerType.OutOfBedConfirmed, timestamp = Fixtures.at(at), data = EventData.OutOfBedConfirmed(evidence = listOf("device_unlocked")))

    @Test
    fun single_onset_and_wake_resolves_the_window() {
        val session = Fixtures.session(
            events = listOf(onset("2026-05-22T22:00:00Z"), outOfBed("2026-05-23T06:00:00Z")),
        )
        val window = requireNotNull(session.detectedSleepWindow())
        assertEquals(Fixtures.at("2026-05-22T22:00:00Z"), window.start)
        assertEquals(Fixtures.at("2026-05-23T06:00:00Z"), window.end)
    }

    @Test
    fun a_rearmed_onset_uses_the_earlier_bedtime_as_start() {
        val session = Fixtures.session(
            events = listOf(
                onset("2026-05-22T22:00:00Z"),
                onset("2026-05-22T23:30:00Z"),
                outOfBed("2026-05-23T06:00:00Z"),
            ),
        )
        val window = requireNotNull(session.detectedSleepWindow())
        assertEquals(Fixtures.at("2026-05-22T22:00:00Z"), window.start)
    }

    @Test
    fun a_duplicate_wake_event_uses_the_latest_as_end() {
        val session = Fixtures.session(
            events = listOf(
                onset("2026-05-22T22:00:00Z"),
                outOfBed("2026-05-23T05:50:00Z"),
                outOfBed("2026-05-23T06:00:00Z"),
            ),
        )
        val window = requireNotNull(session.detectedSleepWindow())
        assertEquals(Fixtures.at("2026-05-23T06:00:00Z"), window.end)
    }

    @Test
    fun no_onset_is_null() {
        val session = Fixtures.session(events = listOf(outOfBed("2026-05-23T06:00:00Z")))
        assertNull(session.detectedSleepWindow())
    }

    @Test
    fun no_wake_is_null() {
        val session = Fixtures.session(events = listOf(onset("2026-05-22T22:00:00Z")))
        assertNull(session.detectedSleepWindow())
    }

    @Test
    fun a_wake_at_or_before_onset_is_malformed_and_null() {
        val session = Fixtures.session(
            events = listOf(onset("2026-05-22T22:00:00Z"), outOfBed("2026-05-22T22:00:00Z")),
        )
        assertNull(session.detectedSleepWindow())
    }
}
