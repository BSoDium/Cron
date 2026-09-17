package fr.bsodium.cron.testutil

import fr.bsodium.cron.memory.MemoryEntry
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
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlin.time.Duration.Companion.hours

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
        isManual: Boolean = false,
    ) = SessionEvent(
        trigger = TriggerType.EveningPlan,
        timestamp = at,
        data = EventData.EveningPlan(
            timezone = "Europe/Vienna",
            location = LocationPayload(lat = lat, lng = lng, source = source, capturedAt = at, address = address),
            isManual = isManual,
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

    fun memoryEntry(
        id: Long = 0,
        text: String = "Prefers earlier wake-ups on gym days",
        category: String? = null,
        createdAt: Instant = T0,
        updatedAt: Instant = T0,
        pending: Boolean = false,
    ) = MemoryEntry(id = id, text = text, category = category, createdAt = createdAt, updatedAt = updatedAt, pending = pending)

    /** [count] sessions, each on its own consecutive calendar date ending at [startingAt] (index 0 is
     *  the most recent), each carrying a same-day SleepOnset/OutOfBedConfirmed event pair. Enough to
     *  exercise real pagination deterministically -- multiple Pager pages, one day-header boundary
     *  between every pair of adjacent sessions -- without HistorySeeder's DB-write-based narrative
     *  realism (callers insert these via SessionDao/EventDao themselves). Built against
     *  [TimeZone.currentSystemDefault] -- the same zone `historyDaySeparator`/`insertDayHeaders` use to
     *  derive a "local date" -- not a fixed zone, so "same calendar date" here actually means the same
     *  thing production code will compute it to mean, regardless of the test machine's own zone. Both
     *  events sit well inside one local day (20h/22h past local midnight) so neither ever wraps into the
     *  next date, and [startingAt]'s default (2026-05-22 minus [count]) keeps every seeded date safely
     *  away from the real "today" a today-suppression check runs against. */
    fun manySessions(count: Int, startingAt: LocalDate = DATE): List<SleepSession> {
        val tz = TimeZone.currentSystemDefault()
        return (0 until count).map { i ->
            val date = startingAt.minus(i, DateTimeUnit.DAY)
            val onset = date.atStartOfDayIn(tz) + 20.hours
            val wake = date.atStartOfDayIn(tz) + 22.hours
            session(
                id = "history-session-$i",
                date = date,
                status = SessionStatus.Complete,
                createdAt = onset,
                updatedAt = wake,
                events = listOf(
                    SessionEvent(trigger = TriggerType.SleepOnset, timestamp = onset, data = EventData.Empty),
                    SessionEvent(trigger = TriggerType.OutOfBedConfirmed, timestamp = wake, data = EventData.Empty),
                ),
            )
        }
    }
}
