package fr.bsodium.cron.ui.screens.home

import fr.bsodium.cron.ai.StreamingTurn
import fr.bsodium.cron.session.db.AiMessageEntity
import fr.bsodium.cron.session.model.EventData
import fr.bsodium.cron.session.model.SessionEvent
import fr.bsodium.cron.session.model.TriggerType
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.util.Locale

/** The session's AI turns as a chronological list of iterations; the last entry is the latest. */
data class AiPlanUi(
    val iterations: List<AiIterationUi>,
)

/** One planning iteration: a [systemMessage] for what triggered it (with the raw [trigger] for its
 *  icon/accent), plus the full [thread] at [timeLabel]. */
data class AiIterationUi(
    val turnIndex: Int,
    val timeLabel: String,
    val systemMessage: String,
    val trigger: TriggerType?,
    val thread: AiThreadUi,
    /** When this turn ran (epoch ms), for the "Ran X ago" footer on older iterations. */
    val ranAtEpochMs: Long? = null,
    /** The base plan (turn 0) was started by the user from the FAB, not the scheduled nightly job. */
    val manualBase: Boolean = false,
)

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
    ): AiPlanUi? {
        val byTurn = rows.groupBy { it.turnIndex }
        val streamingTurn = streaming?.turnIndex
        val turns = (byTurn.keys + listOfNotNull(streamingTurn)).toSortedSet()
        if (turns.isEmpty()) return null

        // The live partial overrides the persisted rows of its turn → never a duplicate/stale iteration.
        fun threadOf(turn: Int): AiThreadUi =
            if (turn == streamingTurn && streaming != null) AiThreadMapper.buildFromBlocks(turn, streaming.blocks)
            else AiThreadMapper.build(byTurn.getValue(turn)) ?: AiThreadUi(turn, summary = null, process = emptyList(), response = null)

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

        val iterations = turns.mapIndexed { index, turn ->
            // The latest event appended at/before this turn started: it names a replan, and for turn 0
            // it's the bootstrap evening-plan — read only to tell a manual FAB plan from the nightly one.
            val start = startOf(turn)
            val sourceEvent = events.filter { it.timestamp.toEpochMilliseconds() <= start }.maxByOrNull { it.timestamp }
            val manualBase = index == 0 && (sourceEvent?.data as? EventData.EveningPlan)?.isManual == true
            // Prefer the seeded trigger for the still-streaming turn: its triggering event may not be persisted
            // yet (the seed beats the event write), so inferring from `events` alone would briefly mislabel it.
            val effectiveTrigger = streaming?.trigger?.takeIf { turn == streamingTurn } ?: sourceEvent?.trigger
            AiIterationUi(
                turnIndex = turn,
                timeLabel = timeLabelOf(turn),
                systemMessage = when {
                    index == 0 && manualBase -> "Planned manually"
                    index == 0 -> "Planned"
                    else -> triggerMessageOf(effectiveTrigger)
                },
                // Turn 0 keeps a null trigger so the scheduled base keeps its clock icon; manualBase drives its icon.
                trigger = if (index == 0) null else effectiveTrigger,
                thread = threadOf(turn),
                ranAtEpochMs = ranAtOf(turn),
                manualBase = manualBase,
            )
        }
        return AiPlanUi(iterations = iterations)
    }

    /** A short "what happened" line for a replan iteration. Exhaustive over [TriggerType]. */
    private fun triggerMessageOf(trigger: TriggerType?): String = when (trigger) {
        null -> "Re-planned"
        TriggerType.EveningPlan -> "Re-planned"
        TriggerType.CalendarChange -> "Your schedule changed"
        TriggerType.SleepOnset -> "You fell asleep"
        TriggerType.HcStageUpdate -> "Sleep update"
        TriggerType.MidSleepActivity -> "Movement detected"
        TriggerType.OutOfBedConfirmed -> "You got up"
        TriggerType.WakeWindowOpportunity -> "A good moment to wake"
        TriggerType.AlarmDismissed -> "Alarm dismissed"
        TriggerType.AlarmSnoozed -> "Alarm snoozed"
        TriggerType.HardLatestFired -> "Safety alarm fired"
    }
}
