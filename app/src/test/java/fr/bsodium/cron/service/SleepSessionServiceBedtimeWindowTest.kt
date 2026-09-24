package fr.bsodium.cron.service

import fr.bsodium.cron.session.model.EventData
import fr.bsodium.cron.session.model.LocationPayload
import fr.bsodium.cron.session.model.LocationSource
import fr.bsodium.cron.session.model.SessionEvent
import fr.bsodium.cron.session.model.TriggerType
import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.time.Duration.Companion.hours

/** The pure bedtime-window arithmetic behind [SleepSessionService.resolveBedtimeWindow]. */
class SleepSessionServiceBedtimeWindowTest {

    private val eveningPlanAt = Instant.parse("2026-05-22T20:00:00Z")
    private val hardLatestAt = Instant.parse("2026-05-23T09:30:00Z")
    private val margin = 4.hours

    @Test
    fun window_spans_evening_plan_through_hard_latest_minus_margin() {
        val window = requireNotNull(SleepSessionService.bedtimeWindowFrom(eveningPlanAt, hardLatestAt, margin))
        assertEquals(eveningPlanAt, window.start)
        assertEquals(hardLatestAt - margin, window.endInclusive)
    }

    @Test
    fun a_margin_that_collapses_the_window_is_null() {
        assertNull(SleepSessionService.bedtimeWindowFrom(eveningPlanAt, hardLatestAt, 14.hours))
    }

    private fun eveningPlanEvent(timestamp: Instant) = SessionEvent(
        trigger = TriggerType.EveningPlan,
        timestamp = timestamp,
        data = EventData.EveningPlan(
            timezone = "Europe/Paris",
            location = LocationPayload(lat = 48.85, lng = 2.35, source = LocationSource.Gps, capturedAt = timestamp),
            isManual = true,
        ),
    )

    @Test
    fun latestEveningPlanAt_picks_the_most_recent_event_regardless_of_list_order() {
        val first = Instant.parse("2026-05-22T20:00:00Z")
        val replan = Instant.parse("2026-05-22T23:00:00Z")
        val events = listOf(eveningPlanEvent(replan), eveningPlanEvent(first))

        assertEquals(replan, SleepSessionService.latestEveningPlanAt(events))
    }

    @Test
    fun latestEveningPlanAt_ignores_unrelated_trigger_types() {
        val onset = SessionEvent(
            trigger = TriggerType.SleepOnset,
            timestamp = Instant.parse("2026-05-23T01:00:00Z"),
            data = EventData.SleepOnset(screenOffSince = Instant.parse("2026-05-23T01:00:00Z"), rearm = false),
        )
        val events = listOf(eveningPlanEvent(eveningPlanAt), onset)

        assertEquals(eveningPlanAt, SleepSessionService.latestEveningPlanAt(events))
    }

    @Test
    fun latestEveningPlanAt_of_no_evening_plan_events_is_null() {
        assertNull(SleepSessionService.latestEveningPlanAt(emptyList()))
    }
}
