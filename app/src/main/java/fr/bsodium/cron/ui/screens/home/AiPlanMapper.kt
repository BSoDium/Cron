package fr.bsodium.cron.ui.screens.home

import fr.bsodium.cron.ai.StreamingTurn
import fr.bsodium.cron.session.db.AiMessageEntity
import fr.bsodium.cron.session.model.SessionEvent
import fr.bsodium.cron.session.model.TriggerType
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.util.Locale

/** The original plan (turn 0) plus the replans that followed it, shown as ChatGPT-style rounds. */
data class AiPlanUi(
    val plan: AiThreadUi,
    val edits: List<AiEditUi>,
)

/** A single replan round: a [systemMessage] for the event that triggered the rerun, plus the full [thread]. */
data class AiEditUi(
    val turnIndex: Int,
    val timeLabel: String,
    val systemMessage: String,
    val thread: AiThreadUi,
)

/**
 * Swaps in the typewriter-[revealed] view of whichever turn is streaming (the plan or the last edit),
 * matched by `turnIndex`. A null [revealed] (nothing streaming) returns this plan unchanged, so a
 * just-settled edit renders from the DB view without a flash.
 */
fun AiPlanUi.withStreamingReplaced(revealed: AiThreadUi?): AiPlanUi {
    if (revealed == null) return this
    if (plan.turnIndex == revealed.turnIndex) return copy(plan = revealed)
    return copy(edits = edits.map { if (it.turnIndex == revealed.turnIndex) it.copy(thread = revealed) else it })
}

/**
 * Folds the session's AI turns into the original [AiPlanUi.plan] (lowest turn) plus the later turns as
 * replan rounds. Each round's [AiEditUi.systemMessage] names the event that triggered the rerun, found
 * by matching the turn's start time against the session [events]. Pure mapping (delegates per-turn
 * rendering to [AiThreadMapper]).
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

        // The live partial overrides the persisted rows of its turn → never a duplicate/stale round.
        fun threadOf(turn: Int): AiThreadUi =
            if (turn == streamingTurn && streaming != null) AiThreadMapper.buildFromBlocks(turn, streaming.blocks)
            else AiThreadMapper.build(byTurn.getValue(turn)) ?: AiThreadUi(turn, summary = null, process = emptyList(), response = null)

        fun startOf(turn: Int): Long =
            byTurn[turn]?.minOfOrNull { it.createdAt }
                ?: streaming?.startedAtMs?.takeIf { turn == streamingTurn }
                ?: Long.MAX_VALUE

        fun timeLabelOf(turn: Int): String {
            val epoch = byTurn[turn]?.maxOfOrNull { it.createdAt }
                ?: streaming?.startedAtMs?.takeIf { turn == streamingTurn }
                ?: return ""
            val local = Instant.fromEpochMilliseconds(epoch).toLocalDateTime(TimeZone.currentSystemDefault())
            return String.format(Locale.US, "%02d:%02d", local.hour, local.minute)
        }

        val plan = threadOf(turns.first())
        val edits = turns.drop(1).map { turn ->
            // The triggering event is the latest one appended at/before the turn started.
            val start = startOf(turn)
            val trigger = events.filter { it.timestamp.toEpochMilliseconds() <= start }.maxByOrNull { it.timestamp }
            AiEditUi(
                turnIndex = turn,
                timeLabel = timeLabelOf(turn),
                systemMessage = triggerMessageOf(trigger),
                thread = threadOf(turn),
            )
        }
        return AiPlanUi(plan = plan, edits = edits)
    }

    /** A short "what happened" line for a replan round. Exhaustive over [TriggerType]. */
    private fun triggerMessageOf(event: SessionEvent?): String = when (event?.trigger) {
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
