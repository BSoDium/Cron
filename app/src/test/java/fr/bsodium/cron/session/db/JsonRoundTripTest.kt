package fr.bsodium.cron.session.db

import fr.bsodium.cron.session.model.ActionType
import fr.bsodium.cron.session.model.ActivityType
import fr.bsodium.cron.session.model.EventData
import fr.bsodium.cron.session.model.Instruction
import fr.bsodium.cron.session.model.LocationPayload
import fr.bsodium.cron.session.model.LocationSource
import fr.bsodium.cron.session.model.SignalConfidence
import fr.bsodium.cron.session.model.SleepStage
import fr.bsodium.cron.testutil.Fixtures
import fr.bsodium.cron.testutil.Fixtures.at
import kotlinx.datetime.LocalTime
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonRoundTripTest {

    private inline fun <reified T> roundTrip(value: T): T =
        SessionJson.decodeFromString(SessionJson.encodeToString(value))

    @Test
    fun every_eventData_variant_round_trips() {
        val variants: List<EventData> = listOf(
            EventData.EveningPlan(
                timezone = "Europe/Paris",
                location = LocationPayload(48.85, 2.35, 12f, LocationSource.Gps, at("2026-05-22T00:00:00Z")),
            ),
            EventData.SleepOnset(screenOffSince = at("2026-05-22T00:00:00Z"), rearm = true),
            EventData.HcStageUpdate(
                SleepStage.Deep, "health_connect", SignalConfidence.High,
                at("2026-05-22T01:00:00Z"), at("2026-05-22T02:00:00Z"),
            ),
            EventData.MidSleepActivity(ActivityType.Walking, screenOn = true, durationSeconds = 30),
            EventData.OutOfBedConfirmed(evidence = listOf("screen_on", "walking")),
            EventData.WakeWindowOpportunity(SleepStage.Light, LocalTime(6, 0), LocalTime(9, 0)),
            EventData.AlarmInteraction(snoozeDurationMinutes = 10, snoozeCount = 2),
            EventData.CalendarChange("modified", "event-1", affectsFirstEvent = true),
            EventData.Empty,
        )

        variants.forEach { variant -> assertEquals(variant, roundTrip(variant)) }
    }

    @Test
    fun eventData_serializes_with_kind_discriminator() {
        val encoded = SessionJson.encodeToString<EventData>(EventData.Empty)
        assertTrue(encoded, encoded.contains("\"kind\""))
        assertTrue(encoded, encoded.contains("\"empty\""))
    }

    @Test
    fun instruction_round_trips() {
        val instruction = Instruction(
            action = ActionType.SetAlarm,
            alarmTime = LocalTime(7, 0),
            reason = "first event at 09:00",
            issuedAt = at("2026-05-22T00:00:00Z"),
        )
        assertEquals(instruction, roundTrip(instruction))
    }

    @Test
    fun sleepSession_round_trips() {
        val session = Fixtures.session(
            events = listOf(Fixtures.hcEvent(SleepStage.Deep, at("2026-05-22T01:00:00Z"), at("2026-05-22T02:00:00Z"))),
        )
        assertEquals(session, roundTrip(session))
    }
}
