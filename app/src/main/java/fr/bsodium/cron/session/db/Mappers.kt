package fr.bsodium.cron.session.db

import fr.bsodium.cron.session.model.DayPlan
import fr.bsodium.cron.session.model.EventData
import fr.bsodium.cron.session.model.Instruction
import fr.bsodium.cron.session.model.SessionEvent
import fr.bsodium.cron.session.model.SessionStatus
import fr.bsodium.cron.session.model.SleepSession
import fr.bsodium.cron.session.model.TriggerType
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.encodeToString

fun SessionEntity.toModel(events: List<SessionEvent>): SleepSession = SleepSession(
    id = id,
    date = LocalDate.parse(date),
    status = SessionStatus.valueOf(status),
    plan = SessionJson.decodeFromString<DayPlan>(planJson),
    currentInstruction = SessionJson.decodeFromString<Instruction>(currentInstructionJson),
    events = events,
    lastAiCallAt = lastAiCallAt?.let { Instant.fromEpochMilliseconds(it) },
    snoozeCount = snoozeCount,
    timezone = timezone,
    cachedFirstEventSig = cachedFirstEventSig,
    createdAt = Instant.fromEpochMilliseconds(createdAt),
    updatedAt = Instant.fromEpochMilliseconds(updatedAt),
)

fun SleepSession.toEntity(): SessionEntity = SessionEntity(
    id = id,
    date = date.toString(),
    status = status.name,
    planJson = SessionJson.encodeToString(plan),
    currentInstructionJson = SessionJson.encodeToString(currentInstruction),
    lastAiCallAt = lastAiCallAt?.toEpochMilliseconds(),
    snoozeCount = snoozeCount,
    timezone = timezone,
    cachedFirstEventSig = cachedFirstEventSig,
    createdAt = createdAt.toEpochMilliseconds(),
    updatedAt = updatedAt.toEpochMilliseconds(),
)

fun SessionEventEntity.toModel(): SessionEvent = SessionEvent(
    trigger = TriggerType.valueOf(trigger),
    timestamp = Instant.fromEpochMilliseconds(timestamp),
    data = SessionJson.decodeFromString<EventData>(dataJson),
)

fun SessionEvent.toEntity(sessionId: String): SessionEventEntity = SessionEventEntity(
    sessionId = sessionId,
    trigger = trigger.name,
    timestamp = timestamp.toEpochMilliseconds(),
    dataJson = SessionJson.encodeToString<EventData>(data),
)
