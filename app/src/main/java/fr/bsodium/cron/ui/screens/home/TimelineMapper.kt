package fr.bsodium.cron.ui.screens.home

import fr.bsodium.cron.session.model.EventData
import fr.bsodium.cron.session.model.SessionEvent
import fr.bsodium.cron.session.model.TriggerType
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime

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
        /** The substring of [detail] to render bold — the key fact in a subtext line, e.g.
         *  "9 extra minutes" in "You get to sleep for 9 extra minutes" — so `EventNode.kt` can
         *  highlight it without a markup language. Always non-null exactly when [detail] is. */
        val detailEmphasis: String? = null,
    ) : TimelineItem {
        override val id = "event-${trigger.name}-${timestamp.toEpochMilliseconds()}"
    }

    /** A day-boundary marker, inserted wherever consecutive items' local dates differ. Purely a
     *  decorative sticky row (`sessionTimelineItems` renders it via `stickyHeader`, never a track
     *  anchor) — it must stay invisible to segment/cap/asleep-state logic (see [timelineAsleepStates]
     *  and `SessionTimeline.kt`'s segment-boundary derivation), which is what a day boundary being a
     *  *structural* break used to get wrong (Round 18): a sleep session spanning midnight had its
     *  asleep state reset and its track split into two capped segments exactly at the boundary. */
    data class DayHeader(
        val date: LocalDate,
        override val timestamp: Instant,
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

/** Diffs [currentIds] against [previousIds] to find ids genuinely new since the last check —
 *  the basis for the timeline's entrance-animation gating (Round 32). [previousIds] is `null`
 *  exactly once, the very first check of the caller's lifetime (true cold start), and that call
 *  always returns [emptySet]: there's no reference point yet, and the first load must render fully
 *  static, never an animated reveal. This is deliberately a pure function, not `remember`-scoped
 *  Compose state — a `remember` resets every time the timeline's composition is torn down and
 *  rebuilt (e.g. a Home→Settings→back round trip), which is exactly the class of bug this replaces
 *  (see `HomeViewModel.NewlyArrivedIdTracker`, which holds the actual `previousIds` across calls at
 *  the ViewModel layer, which survives that navigation). */
fun diffNewlyArrivedIds(currentIds: Set<String>, previousIds: Set<String>?): Set<String> =
    if (previousIds == null) emptySet() else currentIds - previousIds

fun buildTimeline(sessions: List<TimelineSession>): List<TimelineItem> {
    val items = mutableListOf<TimelineItem>()

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
            val detail = eventDetail(event.trigger, event.data)
            items += TimelineItem.Event(
                timestamp = event.timestamp,
                trigger = event.trigger,
                label = eventLabel(event.trigger),
                detail = detail?.text,
                detailEmphasis = detail?.emphasis,
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

    // Defensive: a data-layer race can occasionally surface the same underlying event twice; the LazyColumn key must be unique regardless, so collapse duplicates here rather than let a rare data race crash the UI.
    return insertDayHeaders(withLatest).distinctBy { it.id }
}

/** Threads a [TimelineItem.DayHeader] in front of the first item of each local day — except today's:
 *  the user already knows it's today, so that header would be pure redundancy (Round 27). Purely a
 *  sticky decoration in the rendered list otherwise (see [TimelineItem.DayHeader]'s KDoc) — inserting
 *  it here doesn't by itself affect segment/cap/asleep-state logic, which all explicitly skip past
 *  `DayHeader`s. [lastDate] still tracks today's date even though no header gets emitted for it, so
 *  the boundary into the *next* (non-today) date is still detected and gets its own header. */
private fun insertDayHeaders(items: List<TimelineItem>): List<TimelineItem> {
    val tz = TimeZone.currentSystemDefault()
    val today = Clock.System.now().toLocalDateTime(tz).date
    var lastDate: LocalDate? = null
    return buildList {
        for (item in items) {
            val date = item.timestamp.toLocalDateTime(tz).date
            if (date != lastDate) {
                if (date != today) {
                    add(TimelineItem.DayHeader(date = date, timestamp = date.atStartOfDayIn(tz)))
                }
                lastDate = date
            }
            add(item)
        }
    }
}

/** For each item in [items] (reverse-chronological, latest first), whether the user was asleep
 *  immediately after that item, moving forward in time (toward index 0). Derived purely from the
 *  already-shown SleepOnset/OutOfBedConfirmed rows — no raw session-event access needed. A
 *  [TimelineItem.DayHeader] is a no-op here (Round 18 fix, kept): it must never reset the flag, since
 *  most sleep sessions span midnight. */
fun timelineAsleepStates(items: List<TimelineItem>): List<Boolean> {
    val result = MutableList(items.size) { false }
    var asleep = false
    for (i in items.indices.reversed()) {
        when (val item = items[i]) {
            is TimelineItem.Event -> when (item.trigger) {
                TriggerType.SleepOnset -> asleep = true
                TriggerType.OutOfBedConfirmed -> asleep = false
                else -> {} // every other trigger leaves the asleep/awake state unchanged
            }
            is TimelineItem.AiRun -> {}
            is TimelineItem.DayHeader -> {}
        }
        result[i] = asleep
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

/** [text] is a full subtext sentence for [TimelineItem.Event.detail]; [emphasis] is the substring of
 *  it that should render bold — e.g. "You get to sleep for 9 extra minutes" / "9 extra minutes" for a
 *  snooze (Round 28 — this replaced a separate detail chip, which had no room to say more than the
 *  bare fact, with a subtext line that can say what happened *and* highlight the fact that matters). */
private data class EventDetail(val text: String, val emphasis: String)

private fun eventDetail(trigger: TriggerType, data: EventData): EventDetail? = when {
    // Deterministic, not AI-generated — snoozeDurationMinutes phrased as the upside to the user rather than a bare "N minutes added".
    trigger == TriggerType.AlarmSnoozed && data is EventData.AlarmInteraction ->
        data.snoozeDurationMinutes?.let { minutes ->
            val amount = if (minutes == 1) "1 extra minute" else "$minutes extra minutes"
            EventDetail(text = "You get to sleep for $amount", emphasis = amount)
        }
    // `firstEventTitle` is real calendar data, used directly whenever present; `changeType` is a raw backend identifier, not human-readable text, so it's never interpolated into the subtext.
    trigger == TriggerType.CalendarChange && data is EventData.CalendarChange -> {
        val title = data.firstEventTitle
        if (data.affectsFirstEvent && title != null) {
            EventDetail(text = "$title on your calendar changed", emphasis = title)
        } else {
            val subject = if (data.affectsFirstEvent) "first event" else "calendar"
            EventDetail(text = "Your $subject changed", emphasis = subject)
        }
    }
    else -> null
}
