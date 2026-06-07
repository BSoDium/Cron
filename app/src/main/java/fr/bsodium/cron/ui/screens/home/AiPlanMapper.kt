package fr.bsodium.cron.ui.screens.home

import android.util.Log
import fr.bsodium.cron.ai.StreamingTurn
import fr.bsodium.cron.ai.wire.ContentBlock
import fr.bsodium.cron.session.db.AiMessageEntity
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Locale

/** The original plan (turn 0) plus the replans that followed it, shown as an expandable edits list. */
data class AiPlanUi(
    val plan: AiThreadUi,
    val edits: List<AiEditUi>,
)

/** A single replan, summarised for a one-line row that expands to its full [thread]. */
data class AiEditUi(
    val turnIndex: Int,
    val timeLabel: String,
    val summary: String,
    val thread: AiThreadUi,
    val changedAlarm: Boolean,
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
 * [AiEditUi] edits, so a no-op "changed nothing" replan reads in the context of the plan it edits. Pure
 * over [AiMessageEntity]/[ContentBlock] (delegates per-turn rendering to [AiThreadMapper]).
 */
object AiPlanMapper {

    private val ACTION_TOOLS = setOf("set_alarm", "cancel_alarm", "do_nothing", "send_brief", "notify_warning")
    private const val TAG = "AiPlanMapper"

    fun buildPlan(rows: List<AiMessageEntity>, streaming: StreamingTurn?): AiPlanUi? {
        val byTurn = rows.groupBy { it.turnIndex }
        val streamingTurn = streaming?.turnIndex
        val turns = (byTurn.keys + listOfNotNull(streamingTurn)).toSortedSet()
        if (turns.isEmpty()) return null

        // The live partial overrides the persisted rows of its turn → never a duplicate/stale edit.
        fun assistantBlocksOf(turn: Int): List<ContentBlock> =
            if (turn == streamingTurn && streaming != null) streaming.blocks.filterNot { it is ContentBlock.ToolResult }
            else byTurn[turn].orEmpty().filter { it.role == "assistant" }.flatMap { AiThreadMapper.decodeBlocks(it.contentJson) }

        fun threadOf(turn: Int): AiThreadUi =
            if (turn == streamingTurn && streaming != null) AiThreadMapper.buildFromBlocks(turn, streaming.blocks)
            else AiThreadMapper.build(byTurn.getValue(turn)) ?: AiThreadUi(turn, summary = null, process = emptyList(), response = null)

        fun timeLabelOf(turn: Int): String {
            val epoch = byTurn[turn]?.maxOfOrNull { it.createdAt }
                ?: streaming?.startedAtMs?.takeIf { turn == streamingTurn }
                ?: return ""
            val local = Instant.fromEpochMilliseconds(epoch).toLocalDateTime(TimeZone.currentSystemDefault())
            return String.format(Locale.US, "%02d:%02d", local.hour, local.minute)
        }

        val planTurn = turns.first()
        val plan = threadOf(planTurn)
        // Track the running alarm so an edit reads "Moved to …" only when it actually differs.
        var lastAlarm: LocalTime? = alarmOf(assistantBlocksOf(planTurn))
        val edits = turns.drop(1).map { turn ->
            val blocks = assistantBlocksOf(turn)
            val thread = threadOf(turn)
            val alarm = alarmOf(blocks)
            val moved = alarm != null && lastAlarm != null && alarm != lastAlarm
            val summary = summaryOf(blocks, alarm, moved, thread)
            if (alarm != null) lastAlarm = alarm
            AiEditUi(
                turnIndex = turn,
                timeLabel = timeLabelOf(turn),
                summary = summary,
                thread = thread,
                changedAlarm = terminalTool(blocks)?.name in CHANGED_ALARM_TOOLS,
            )
        }
        return AiPlanUi(plan = plan, edits = edits)
    }

    private val CHANGED_ALARM_TOOLS = setOf("set_alarm", "cancel_alarm")

    private fun terminalTool(blocks: List<ContentBlock>): ContentBlock.ToolUse? =
        blocks.filterIsInstance<ContentBlock.ToolUse>().lastOrNull { it.name in ACTION_TOOLS }

    private fun alarmOf(blocks: List<ContentBlock>): LocalTime? {
        val tool = terminalTool(blocks)?.takeIf { it.name == "set_alarm" } ?: return null
        return runCatching {
            val iso = tool.input.jsonObject["time_iso"]?.jsonPrimitive?.content ?: return null
            Instant.parse(iso).toLocalDateTime(TimeZone.currentSystemDefault()).time
        }.onFailure { Log.w(TAG, "read set_alarm time_iso failed", it) }.getOrNull()
    }

    private fun summaryOf(blocks: List<ContentBlock>, alarm: LocalTime?, moved: Boolean, thread: AiThreadUi): String {
        val tool = terminalTool(blocks) ?: return thread.summary ?: "Thinking…"
        // tool.name is open model input — `else` covers an unknown future tool.
        return when (tool.name) {
            "set_alarm" -> alarm?.let { String.format(Locale.US, if (moved) "Moved to %02d:%02d" else "Set %02d:%02d", it.hour, it.minute) } ?: "Set alarm"
            "cancel_alarm" -> "Cancelled alarm"
            "send_brief" -> "Sent brief"
            "notify_warning" -> reasonOf(tool, "message")?.let { "Warning · ${it.take(40)}" } ?: "Warning"
            "do_nothing" -> reasonOf(tool, "reason")?.let { "No change · $it" } ?: "No change"
            else -> thread.summary ?: "Thinking…"
        }
    }

    private fun reasonOf(tool: ContentBlock.ToolUse, key: String): String? =
        runCatching { tool.input.jsonObject[key]?.jsonPrimitive?.content }
            .onFailure { Log.w(TAG, "read $key failed", it) }
            .getOrNull()?.takeIf { it.isNotBlank() }
}
