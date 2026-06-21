package fr.bsodium.cron.ui.screens.home

import fr.bsodium.cron.session.model.EventData
import fr.bsodium.cron.session.model.SessionEvent
import fr.bsodium.cron.session.model.TriggerType
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toLocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** One item in the vertical timeline. Ordered reverse-chronologically (latest first). */
sealed interface TimelineItem {
    val timestamp: Instant
    val id: String

    data class DayHeader(
        override val timestamp: Instant,
        val label: String,
    ) : TimelineItem {
        override val id = "day-$label"
    }

    data class AiRun(
        override val timestamp: Instant,
        val iteration: AiIterationUi,
        val sessionId: String,
        val isStreaming: Boolean,
        val isLatest: Boolean,
    ) : TimelineItem {
        override val id = "ai-$sessionId-${iteration.turnIndex}"
    }

    data class Event(
        override val timestamp: Instant,
        val trigger: TriggerType,
        val label: String,
        val detail: String?,
    ) : TimelineItem {
        override val id = "event-${trigger.name}-${timestamp.toEpochMilliseconds()}"
    }
}

/** Input to [buildTimeline]: one session's worth of data, pre-mapped. */
data class TimelineSession(
    val sessionId: String,
    val iterations: List<AiIterationUi>,
    val events: List<SessionEvent>,
    val streamingTurnIndex: Int?,
)

private val SHOWN_TRIGGERS = setOf(
    TriggerType.SleepOnset,
    TriggerType.AlarmDismissed,
    TriggerType.AlarmSnoozed,
    TriggerType.OutOfBedConfirmed,
    TriggerType.CalendarChange,
    TriggerType.HardLatestFired,
    TriggerType.WakeWindowOpportunity,
)

fun buildTimeline(sessions: List<TimelineSession>): List<TimelineItem> {
    val items = mutableListOf<TimelineItem>()
    val tz = TimeZone.currentSystemDefault()

    for (session in sessions) {
        val aiTurnTimestamps = session.iterations.mapNotNull { it.ranAtEpochMs }.toSet()

        for (iter in session.iterations) {
            val ts = iter.ranAtEpochMs?.let { Instant.fromEpochMilliseconds(it) } ?: continue
            items += TimelineItem.AiRun(
                timestamp = ts,
                iteration = iter,
                sessionId = session.sessionId,
                isStreaming = iter.turnIndex == session.streamingTurnIndex,
                isLatest = iter == session.iterations.lastOrNull(),
            )
        }

        for (event in session.events) {
            if (event.trigger !in SHOWN_TRIGGERS) continue
            if (event.trigger == TriggerType.EveningPlan) continue
            items += TimelineItem.Event(
                timestamp = event.timestamp,
                trigger = event.trigger,
                label = eventLabel(event.trigger),
                detail = eventDetail(event.trigger, event.data),
            )
        }
    }

    items.sortByDescending { it.timestamp }

    val result = mutableListOf<TimelineItem>()
    var currentDate: LocalDate? = null
    for (item in items) {
        val date = item.timestamp.toLocalDateTime(tz).date
        if (date != currentDate) {
            currentDate = date
            result += TimelineItem.DayHeader(
                timestamp = item.timestamp,
                label = formatTimelineDateLabel(date),
            )
        }
        result += item
    }
    return result
}

private fun eventLabel(trigger: TriggerType): String = when (trigger) {
    TriggerType.SleepOnset -> "You fell asleep"
    TriggerType.AlarmDismissed -> "Alarm dismissed"
    TriggerType.AlarmSnoozed -> "Alarm snoozed"
    TriggerType.OutOfBedConfirmed -> "You got up"
    TriggerType.CalendarChange -> "Your schedule changed"
    TriggerType.HardLatestFired -> "Safety alarm fired"
    TriggerType.WakeWindowOpportunity -> "A good moment to wake"
    TriggerType.EveningPlan -> "Evening plan"
    TriggerType.HcStageUpdate -> "Sleep update"
    TriggerType.MidSleepActivity -> "Movement detected"
}

private fun eventDetail(trigger: TriggerType, data: EventData): String? = when {
    trigger == TriggerType.AlarmSnoozed && data is EventData.AlarmInteraction ->
        data.snoozeDurationMinutes?.let { "${it} min" }
    trigger == TriggerType.CalendarChange && data is EventData.CalendarChange ->
        data.changeType.replaceFirstChar { it.uppercase(Locale.ROOT) }
    else -> null
}

// locale-default weekday name is intentional here (human-language)
private fun formatTimelineDateLabel(date: LocalDate): String {
    val today = java.time.LocalDate.now()
    val jDate = date.toJavaLocalDate()
    return when (ChronoUnit.DAYS.between(jDate, today)) {
        0L -> "Today"
        1L -> "Yesterday"
        else -> jDate.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()))
    }
}
