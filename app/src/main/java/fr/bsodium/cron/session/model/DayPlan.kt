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
