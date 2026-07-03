package fr.bsodium.cron.ui.screens.home

import fr.bsodium.cron.ai.StreamingTurn
import fr.bsodium.cron.session.db.AiMessageEntity
import fr.bsodium.cron.session.model.EventData
import fr.bsodium.cron.session.model.SessionEvent
import fr.bsodium.cron.session.model.TriggerType
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.util.Locale

/** The session's AI turns as a chronological list of iterations; the last entry is the latest. */
data class AiPlanUi(
    val iterations: List<AiIterationUi>,
)

/** What kind of run produced an iteration — the single source for its tab label and icon. */
sealed interface RunKind {
    /** The nightly scheduled base plan. */
    data object ScheduledBase : RunKind

    /** A base plan the user started from the FAB. */
    data object ManualBase : RunKind

    /** A later rerun, named by the event that triggered it (null trigger → generic "Re-planned"). */
    data class Replan(val trigger: TriggerType?, val rearm: Boolean = false) : RunKind
}

/** The tab label for a run. Exhaustive over [RunKind] and [TriggerType]. */
val RunKind.label: String
    get() = when (this) {
        RunKind.ScheduledBase -> "Planned"
        RunKind.ManualBase -> "Planned manually"
        is RunKind.Replan -> when (trigger) {
            null -> "Re-planned"
            TriggerType.EveningPlan -> "Re-planned"
            TriggerType.CalendarChange -> "Your schedule changed"
            TriggerType.SleepOnset -> if (rearm) "You fell back asleep" else "You fell asleep"
            TriggerType.HcStageUpdate -> "Sleep update"
            TriggerType.MidSleepActivity -> "Movement detected"
            TriggerType.OutOfBedConfirmed -> "You got up"
            TriggerType.WakeWindowOpportunity -> "A good moment to wake"
            TriggerType.AlarmDismissed -> "Alarm dismissed"
            TriggerType.AlarmSnoozed -> "Alarm snoozed"
            TriggerType.HardLatestFired -> "Safety alarm fired"
        }
    }

/** One planning iteration: its [kind] (label/icon source) plus the full [thread] at [timeLabel]. */
data class AiIterationUi(
    val turnIndex: Int,
    val timeLabel: String,
    val kind: RunKind,
    val thread: AiThreadUi,
    /** When this turn ran (epoch ms), for the "Ran X ago" footer on older iterations. */
    val ranAtEpochMs: Long? = null,
    /** The last earlier turn's [AiThreadUi.newAlarmTime] in this session (skipping over any turn that
     *  didn't set one), or null if no earlier turn ever resolved a time — the timeline hero row's
     *  "before" value for a PREV › NEW headline. Never looks across session boundaries. */
    val previousAlarmTime: LocalTime? = null,
) {
    /** The tab label for this run. */
    val systemMessage: String get() = if (thread.isMocked) "Test plan" else kind.label
}

/**
 * Swaps in the typewriter-[revealed] view of whichever iteration is streaming, matched by `turnIndex`.
 * A null [revealed] (nothing streaming) returns this plan unchanged, so a just-settled iteration renders
 * from the DB view without a flash.
 */
fun AiPlanUi.withStreamingReplaced(revealed: AiThreadUi?): AiPlanUi {
    if (revealed == null) return this
    return copy(iterations = iterations.map { if (it.turnIndex == revealed.turnIndex) it.copy(thread = revealed) else it })
}

/**
 * Folds the session's AI turns into a chronological list of [AiIterationUi]. The original (lowest) turn
 * reads "Planned"; each later turn's [AiIterationUi.systemMessage] names the event that triggered the
 * rerun, found by matching the turn's start time against the session [events]. Pure mapping (delegates
 * per-turn rendering to [AiThreadMapper]).
 */
object AiPlanMapper {

    fun buildPlan(
        rows: List<AiMessageEntity>,
        streaming: StreamingTurn?,
        events: List<SessionEvent>,
        // Builds a settled turn's thread; callers pass a memoizing impl so immutable turns aren't re-decoded per emission.
        threadFor: (turn: Int, rows: List<AiMessageEntity>) -> AiThreadUi = { turn, turnRows ->
            AiThreadMapper.build(turnRows) ?: AiThreadUi(turn, summary = null, process = emptyList(), response = null)
        },
    ): AiPlanUi? {
        val byTurn = rows.groupBy { it.turnIndex }
        val streamingTurn = streaming?.turnIndex
        val turns = (byTurn.keys + listOfNotNull(streamingTurn)).toSortedSet()
        if (turns.isEmpty()) return null

        // The live partial overrides the persisted rows of its turn → never a duplicate/stale iteration.
        fun threadOf(turn: Int): AiThreadUi =
            if (turn == streamingTurn) {
                requireNotNull(streaming) { "streamingTurn is non-null only when streaming is set" }
                AiThreadMapper.buildFromBlocks(turn, streaming.blocks, isMocked = streaming.isMocked)
            } else {
                threadFor(turn, byTurn.getValue(turn))
            }

        fun startOf(turn: Int): Long =
            byTurn[turn]?.minOfOrNull { it.createdAt }
                ?: streaming?.startedAtMs?.takeIf { turn == streamingTurn }
                ?: Long.MAX_VALUE

        fun ranAtOf(turn: Int): Long? =
            byTurn[turn]?.maxOfOrNull { it.createdAt }
                ?: streaming?.startedAtMs?.takeIf { turn == streamingTurn }

        fun timeLabelOf(turn: Int): String {
            val epoch = ranAtOf(turn) ?: return ""
            val local = Instant.fromEpochMilliseconds(epoch).toLocalDateTime(TimeZone.currentSystemDefault())
            return String.format(Locale.US, "%02d:%02d", local.hour, local.minute)
        }

        // Built once per turn up front (not inline per-iteration) so a later iteration's previousAlarmTime
        // lookup can read an earlier turn's already-computed thread instead of re-decoding it.
        val orderedTurns = turns.toList()
        val threadsByTurn = orderedTurns.associateWith { threadOf(it) }

        val iterations = orderedTurns.mapIndexed { index, turn ->
            // Latest event at/before this turn's start names the replan (or, for turn 0, the bootstrap evening-plan); lastOrNull breaks equal-timestamp ties toward the latest-appended event.
            val start = startOf(turn)
            val sourceEvent = events.lastOrNull { it.timestamp.toEpochMilliseconds() <= start }
            // Prefer the seeded trigger for the still-streaming turn: it may not be persisted to `events` yet (the seed beats the event write).
            val effectiveTrigger = streaming?.trigger?.takeIf { turn == streamingTurn } ?: sourceEvent?.trigger
            val kind = when {
                index > 0 -> RunKind.Replan(
                    trigger = effectiveTrigger,
                    rearm = (sourceEvent?.data as? EventData.SleepOnset)?.rearm == true,
                )
                (sourceEvent?.data as? EventData.EveningPlan)?.isManual == true -> RunKind.ManualBase
                else -> RunKind.ScheduledBase
            }
            // Skips over any intervening turn that didn't resolve a time (e.g. do_nothing), so the
            // "before" value is always the last turn that actually set one, not just the immediate prior turn.
            val previousAlarmTime = orderedTurns.take(index).asReversed()
                .firstNotNullOfOrNull { threadsByTurn.getValue(it).newAlarmTime }
            AiIterationUi(
                turnIndex = turn,
                timeLabel = timeLabelOf(turn),
                kind = kind,
                thread = threadsByTurn.getValue(turn),
                ranAtEpochMs = ranAtOf(turn),
                previousAlarmTime = previousAlarmTime,
            )
        }
        return AiPlanUi(iterations = iterations)
    }
}
