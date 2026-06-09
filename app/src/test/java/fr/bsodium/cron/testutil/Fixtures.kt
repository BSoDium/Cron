package fr.bsodium.cron.testutil

import fr.bsodium.cron.session.model.ActionType
import fr.bsodium.cron.session.model.DayPlan
import fr.bsodium.cron.session.model.EventData
import fr.bsodium.cron.session.model.Instruction
import fr.bsodium.cron.session.model.LocationPayload
import fr.bsodium.cron.session.model.LocationSource
import fr.bsodium.cron.session.model.SessionEvent
import fr.bsodium.cron.session.model.SessionStatus
import fr.bsodium.cron.session.model.SignalConfidence
import fr.bsodium.cron.session.model.SleepSession
import fr.bsodium.cron.session.model.SleepStage
import fr.bsodium.cron.session.model.TriggerType
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/** Deterministic sample-data builders shared across the unit + Robolectric tests. */
object Fixtures {
    val DATE: LocalDate = LocalDate.parse("2026-05-22")
    val T0: Instant = Instant.parse("2026-05-22T00:00:00Z")

    fun at(iso: String): Instant = Instant.parse(iso)

    fun dayPlan(
        hardLatest: LocalTime = LocalTime(10, 0),
        wakeWindowStart: LocalTime = LocalTime(6, 0),
        wakeWindowEnd: LocalTime = LocalTime(9, 0),
        commuteBufferMinutes: Int = 30,
        isFreeDayFallback: Boolean = false,
        generatedAt: Instant = T0,
    ) = DayPlan(
        hardLatest = hardLatest,
        wakeWindowStart = wakeWindowStart,
        wakeWindowEnd = wakeWindowEnd,
        commuteBufferMinutes = commuteBufferMinutes,
        isFreeDayFallback = isFreeDayFallback,
        generatedAt = generatedAt,
    )

    fun instruction(
        action: ActionType = ActionType.SetAlarm,
        alarmTime: LocalTime? = LocalTime(7, 0),
        reason: String = "test instruction",
        issuedAt: Instant = T0,
    ) = Instruction(action = action, alarmTime = alarmTime, reason = reason, issuedAt = issuedAt)

    fun eveningEvent(
        lat: Double = 46.624,
        lng: Double = 14.308,
        address: String? = "Klagenfurt, Austria",
        source: LocationSource = LocationSource.Gps,
        at: Instant = T0,
    ) = SessionEvent(
        trigger = TriggerType.EveningPlan,
        timestamp = at,
        data = EventData.EveningPlan(
            timezone = "Europe/Vienna",
            location = LocationPayload(lat = lat, lng = lng, source = source, capturedAt = at, address = address),
        ),
    )

    fun hcEvent(stage: SleepStage, start: Instant, end: Instant) = SessionEvent(
        trigger = TriggerType.HcStageUpdate,
        timestamp = start,
        data = EventData.HcStageUpdate(
            stage = stage,
            source = "test",
            confidence = SignalConfidence.High,
            recordStart = start,
            recordEnd = end,
        ),
    )

    fun session(
        id: String = "session-1",
        date: LocalDate = DATE,
        status: SessionStatus = SessionStatus.Monitoring,
        plan: DayPlan = dayPlan(),
        currentInstruction: Instruction = instruction(),
        events: List<SessionEvent> = emptyList(),
        timezone: String = "Europe/Paris",
        createdAt: Instant = T0,
        updatedAt: Instant = T0,
    ) = SleepSession(
        id = id,
        date = date,
        status = status,
        plan = plan,
        currentInstruction = currentInstruction,
        events = events,
        timezone = timezone,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}
