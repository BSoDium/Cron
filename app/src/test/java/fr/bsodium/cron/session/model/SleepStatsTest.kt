package fr.bsodium.cron.session.model

import fr.bsodium.cron.testutil.Fixtures
import fr.bsodium.cron.testutil.Fixtures.at
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.hours

class SleepStatsTest {

    @Test
    fun sleepSegments_empty_when_no_hc_events() {
        val onset = SessionEvent(
            trigger = TriggerType.SleepOnset,
            timestamp = at("2026-05-22T00:30:00Z"),
            data = EventData.SleepOnset(screenOffSince = at("2026-05-22T00:00:00Z"), rearm = false),
        )
        assertEquals(emptyList<SleepSegment>(), Fixtures.session(events = listOf(onset)).sleepSegments())
    }

    @Test
    fun sleepSegments_filters_non_hc_events_and_sorts_by_start() {
        val late = Fixtures.hcEvent(SleepStage.Deep, at("2026-05-22T01:00:00Z"), at("2026-05-22T02:00:00Z"))
        val early = Fixtures.hcEvent(SleepStage.Light, at("2026-05-22T00:00:00Z"), at("2026-05-22T01:00:00Z"))
        val noise = SessionEvent(
            trigger = TriggerType.SleepOnset,
            timestamp = at("2026-05-22T00:30:00Z"),
            data = EventData.SleepOnset(screenOffSince = at("2026-05-22T00:00:00Z"), rearm = false),
        )

        val segments = Fixtures.session(events = listOf(late, noise, early)).sleepSegments()

        assertEquals(2, segments.size)
        assertEquals(SleepStage.Light, segments[0].stage)
        assertEquals(SleepStage.Deep, segments[1].stage)
    }

    @Test
    fun timeInBed_is_zero_without_stage_data() {
        assertEquals(ZERO, Fixtures.session().timeInBed())
    }

    @Test
    fun timeInBed_spans_first_start_to_last_end() {
        val first = Fixtures.hcEvent(SleepStage.Light, at("2026-05-22T00:00:00Z"), at("2026-05-22T01:00:00Z"))
        val last = Fixtures.hcEvent(SleepStage.Deep, at("2026-05-22T02:00:00Z"), at("2026-05-22T05:00:00Z"))

        assertEquals(5.hours, Fixtures.session(events = listOf(last, first)).timeInBed())
    }
}
