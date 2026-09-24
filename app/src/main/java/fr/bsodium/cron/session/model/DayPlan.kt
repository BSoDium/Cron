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

/** The most recent [TriggerType.EveningPlan] event by timestamp, not list/append order — a replan
 *  appends a new event rather than replacing the old one, and events aren't guaranteed to be stored in
 *  timestamp order (see [fr.bsodium.cron.session.db.EventDao]). The one selector every consumer of
 *  "the latest evening plan" must share, so two independent implementations can't disagree. */
fun latestEveningPlanEvent(events: List<SessionEvent>): SessionEvent? =
    events.filter { it.trigger == TriggerType.EveningPlan }.maxByOrNull { it.timestamp }

/** The LATEST evening-plan location fix. A manual replan exists precisely to capture a fresh fix after
 *  the user moved, so every consumer (prompt text, commute origin bias) must read this one helper —
 *  reading the bootstrap's first event routes commutes from a stale location. */
fun SleepSession.latestEveningPlanLocation(): LocationPayload? =
    (latestEveningPlanEvent(events)?.data as? EventData.EveningPlan)?.location

data class SleepWindow(val start: Instant, val end: Instant)

/** Cron's own coarse asleep→awake window for this session.
 *
 *  Start is the [EventData.SleepOnset.screenOffSince] of the earliest [TriggerType.SleepOnset]
 *  (later ones are re-arms after an interruption, not a new bedtime) — not the onset's emission
 *  timestamp, which lags the real screen-off by the onset threshold (20-40+ min).
 *
 *  End is the latest [TriggerType.OutOfBedConfirmed] when one exists -- it's the more direct signal
 *  and reaching Awake that way doesn't cancel an already-scheduled alarm, so a later, unrelated
 *  [TriggerType.AlarmDismissed] must not override a genuine earlier wake. Only falls back to the
 *  latest [TriggerType.AlarmDismissed] when no [TriggerType.OutOfBedConfirmed] exists at all: many
 *  sessions end by the user dismissing the alarm without ever holding an unlock long enough to
 *  confirm out-of-bed (e.g. a brief dismiss-and-drop-the-phone-back-down), and treating those as
 *  "no wake detected" silently drops them from history and from the Health Connect write.
 *
 *  Null if either boundary is missing or the window is malformed —
 *  [androidx.health.connect.client.records.SleepSessionRecord] rejects a start not strictly before end. */
fun SleepSession.detectedSleepWindow(): SleepWindow? {
    val onsetEvent = events.filter { it.trigger == TriggerType.SleepOnset }.minByOrNull { it.timestamp } ?: return null
    val start = (onsetEvent.data as? EventData.SleepOnset)?.screenOffSince ?: onsetEvent.timestamp
    val end = events.filter { it.trigger == TriggerType.OutOfBedConfirmed }.maxByOrNull { it.timestamp }?.timestamp
        ?: events.filter { it.trigger == TriggerType.AlarmDismissed }.maxByOrNull { it.timestamp }?.timestamp
        ?: return null
    if (end <= start) return null
    return SleepWindow(start, end)
}
