package fr.bsodium.cron.session.model

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.serialization.Serializable

@Serializable
data class DayPlan(
    val hardLatest: LocalTime,
    val wakeWindowStart: LocalTime,
    val wakeWindowEnd: LocalTime,
    val firstEventId: String? = null,
    val firstEventTime: Instant? = null,
    val firstEventLocation: String? = null,
    val commuteBufferMinutes: Int,
    val preparationBufferMinutes: Int = 15,
    val allowedCommuteModes: Set<CommuteMode> = CommuteMode.entries.toSet(),
    val isFreeDayFallback: Boolean,
    val generatedAt: Instant,
)

@Serializable
data class SleepSession(
    val id: String,
    val date: LocalDate,
    val status: SessionStatus,
    val plan: DayPlan,
    val currentInstruction: Instruction,
    val events: List<SessionEvent>,
    val lastAiCallAt: Instant? = null,
    val snoozeCount: Int = 0,
    val timezone: String,
    val cachedFirstEventSig: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
)

/** The LATEST evening-plan location fix. A manual replan exists precisely to capture a fresh fix after
 *  the user moved, so every consumer (prompt text, commute origin bias) must read this one helper —
 *  reading the bootstrap's first event routes commutes from a stale location. */
fun SleepSession.latestEveningPlanLocation(): LocationPayload? =
    (events.lastOrNull { it.trigger == TriggerType.EveningPlan }?.data as? EventData.EveningPlan)?.location
