package fr.bsodium.cron.ui.screens.home

import fr.bsodium.cron.session.model.EventData
import fr.bsodium.cron.session.model.SessionEvent
import fr.bsodium.cron.session.model.TriggerType
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toLocalDateTime
import java.time.LocalDate as JavaLocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** One item in the vertical timeline. Ordered reverse-chronologically (latest first). */
sealed interface TimelineItem {
    val timestamp: Instant
    val id: String

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

    /** A day boundary marker, inserted wherever consecutive items' local dates differ. */
    data class DayHeader(
        val date: LocalDate,
        override val timestamp: Instant,
        val weekdayLabel: String,
        val dateLabel: String,
    ) : TimelineItem {
        override val id = "day-$date"
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

/** Result of [capTimeline]: the display-bound slice of a timeline, plus whether anything was cut off. */
data class CappedTimeline(val items: List<TimelineItem>, val truncated: Boolean)

private const val TIMELINE_ITEM_CAP = 24

/** Bounds a (already latest-first) timeline to [cap] content items — the home screen renders this, not
 *  the raw merged list, since an unbounded number of sessions/iterations otherwise makes it laggy to
 *  compose. [DayHeader]s ride along for free and a trailing dangling one is dropped, so the cut never
 *  leaves an empty day heading as the last visible row. */
fun capTimeline(items: List<TimelineItem>, cap: Int = TIMELINE_ITEM_CAP): CappedTimeline {
    val result = mutableListOf<TimelineItem>()
    var contentCount = 0
    for (item in items) {
        if (item !is TimelineItem.DayHeader && contentCount >= cap) break
        result += item
        if (item !is TimelineItem.DayHeader) contentCount++
    }
    while (result.lastOrNull() is TimelineItem.DayHeader) result.removeAt(result.lastIndex)
    val totalContent = items.count { it !is TimelineItem.DayHeader }
    return CappedTimeline(items = result, truncated = totalContent > cap)
}

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
                isLatest = false,
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

    var latestFound = false
    val withLatest = items.map { item ->
        if (item is TimelineItem.AiRun && !latestFound) {
            latestFound = true
            item.copy(isLatest = true)
        } else {
            item
        }
    }

    // Defensive: a data-layer race can occasionally surface the same underlying event twice (e.g.
    // across a session boundary); the LazyColumn key must be unique regardless, so collapse duplicates
    // here rather than let a rare data race crash the UI.
    return insertDayHeaders(withLatest, tz).distinctBy { it.id }
}

/** Threads a [TimelineItem.DayHeader] in front of the first item of each local day. */
private fun insertDayHeaders(items: List<TimelineItem>, tz: TimeZone): List<TimelineItem> {
    var lastDate: LocalDate? = null
    return buildList {
        for (item in items) {
            val date = item.timestamp.toLocalDateTime(tz).date
            if (date != lastDate) {
                add(dayHeader(date, tz))
                lastDate = date
            }
            add(item)
        }
    }
}

private fun dayHeader(date: LocalDate, tz: TimeZone): TimelineItem.DayHeader {
    val today = JavaLocalDate.now()
    val javaDate = date.toJavaLocalDate()
    val relativeLabel = when (ChronoUnit.DAYS.between(javaDate, today)) {
        0L -> "Today"
        1L -> "Yesterday"
        // locale-default weekday name is intentional here (human-language); any other day gap (unbounded)
        else -> javaDate.format(DateTimeFormatter.ofPattern("EEEE", Locale.getDefault()))
    }
    // locale-default uppercasing of a human-language label, purely for the header's display styling
    val weekdayLabel = relativeLabel.uppercase(Locale.getDefault())
    // locale-default month abbreviation is intentional here (human-language)
    val dateLabel = javaDate.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))
        .uppercase(Locale.getDefault())
    return TimelineItem.DayHeader(
        date = date,
        timestamp = date.atStartOfDayIn(tz),
        weekdayLabel = weekdayLabel,
        dateLabel = dateLabel,
    )
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
