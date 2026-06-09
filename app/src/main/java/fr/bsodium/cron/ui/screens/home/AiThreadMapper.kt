package fr.bsodium.cron.ui.screens.home

import android.util.Log
import fr.bsodium.cron.ai.wire.ContentBlock
import fr.bsodium.cron.session.db.AiMessageEntity
import fr.bsodium.cron.session.db.SessionJson
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Locale

data class AiThreadUi(
    val turnIndex: Int,
    val summary: String?,
    val process: List<ProcessItem>,
    val response: String?,
    /** Wall-clock seconds the turn took — shown as "Thought for Xs" once settled. */
    val durationSeconds: Int? = null,
    /** True while this thread is being streamed live (vs. read back from the DB). Drives haptics. */
    val isStreaming: Boolean = false,
)

/** One ordered step of the assistant's thinking process, shown inside the collapsible. */
sealed interface ProcessItem {
    data class Reasoning(val text: String) : ProcessItem
    data class Narration(val text: String) : ProcessItem
    data class Tool(
        val name: String,
        val isComplete: Boolean,
        /** Short result summary (e.g. "12 events"), shown in place of a checkmark. */
        val contextLabel: String? = null,
        /** The tool returned an error result — shown as a warning glyph instead of a check. */
        val isError: Boolean = false,
    ) : ProcessItem
}

/**
 * Turns one turn's persisted AI message rows (or its in-flight streamed blocks) into the [AiThreadUi]
 * the home screen renders. Pure mapping over [AiMessageEntity] / [ContentBlock] — no Android or
 * ViewModel coupling, so it's unit-testable in isolation.
 */
object AiThreadMapper {

    /** DB path: decode the latest turn's rows, then map. The settled source of truth. */
    fun build(rows: List<AiMessageEntity>): AiThreadUi? {
        if (rows.isEmpty()) return null
        val latestTurn = rows.maxOf { it.turnIndex }
        val turnRows = rows.filter { it.turnIndex == latestTurn }
        val assistantBlocks = turnRows.filter { it.role == "assistant" }.flatMap { decodeBlocks(it.contentJson) }
        val toolResultBlocks = turnRows.filter { it.role == "user" }.flatMap { decodeBlocks(it.contentJson) }
            .filterIsInstance<ContentBlock.ToolResult>()
        if (assistantBlocks.isEmpty()) return AiThreadUi(
            turnIndex = latestTurn,
            summary = "Thinking…",
            process = emptyList(),
            response = null,
        )
        val durationSeconds = ((turnRows.maxOf { it.createdAt } - turnRows.minOf { it.createdAt }) / 1000).toInt()
        return buildFromBlocks(latestTurn, assistantBlocks, toolResultBlocks, durationSeconds, isStreaming = false)
    }

    /**
     * Streaming path: the in-flight turn's blocks (assistant content + tool_result, interleaved) are
     * already in hand. Produces the same [AiThreadUi] shape as [build] so the live last frame and the
     * settled first frame render identically — no flicker at hand-off.
     */
    fun buildFromBlocks(turnIndex: Int, blocks: List<ContentBlock>): AiThreadUi {
        val assistantBlocks = blocks.filterNot { it is ContentBlock.ToolResult }
        val toolResultBlocks = blocks.filterIsInstance<ContentBlock.ToolResult>()
        if (assistantBlocks.isEmpty()) return AiThreadUi(
            turnIndex = turnIndex,
            summary = "Thinking…",
            process = emptyList(),
            response = null,
            isStreaming = true,
        )
        // "Thought for Xs" is a settled-only affordance — duration stays null while streaming.
        return buildFromBlocks(turnIndex, assistantBlocks, toolResultBlocks, durationSeconds = null, isStreaming = true)
    }

    private fun buildFromBlocks(
        turnIndex: Int,
        blocks: List<ContentBlock>,
        toolResultBlocks: List<ContentBlock.ToolResult>,
        durationSeconds: Int?,
        isStreaming: Boolean,
    ): AiThreadUi {
        val toolResults = toolResultBlocks.associateBy { it.tool_use_id }

        // Model-authored pill labels: "STATUS: <gerund>" while working, leading "SUMMARY: <past>" on
        // the answer. Pull them out in order and strip them so they never render.
        val statuses = mutableListOf<String>()
        var summaryLine: String? = null
        // [committable] = false for the still-streaming tail block: a STATUS gerund may be half-typed
        // ("Evaluating"…), so strip it from display but don't surface it as the live step title yet.
        fun stripDirectives(text: String, committable: Boolean = true): String {
            val kept = mutableListOf<String>()
            text.lineSequence().forEach { line ->
                val trimmed = line.trim()
                val status = STATUS_LINE.matchEntire(trimmed)
                val summary = SUMMARY_LINE.matchEntire(trimmed)
                when {
                    status != null -> if (committable) status.groupValues[1].trim().takeIf { it.isNotEmpty() }?.let { statuses += it }
                    summary != null -> if (committable) summary.groupValues[1].trim().takeIf { it.isNotEmpty() }?.let { summaryLine = it }
                    else -> kept += line
                }
            }
            // While streaming, a trailing line still typing out a directive ("STATU", "SUMMAR") hasn't
            // matched the full STATUS:/SUMMARY: regex yet — drop it so the prefix never flashes.
            if (isStreaming && kept.lastOrNull()?.let { isPartialDirectivePrefix(it.trim()) } == true) {
                kept.removeAt(kept.lastIndex)
            }
            return kept.joinToString("\n").trim()
        }

        // The answer begins at the model's SUMMARY marker (see [answerStartOf]); everything before it is
        // thinking-process narration. While streaming, nothing is the answer until the marker lands.
        val answerStart = answerStartOf(blocks, isStreaming)

        val process = blocks.take(answerStart).mapIndexedNotNull { index, block ->
            when (block) {
                is ContentBlock.Thinking ->
                    block.thinking.takeIf { it.isNotBlank() }?.let { ProcessItem.Reasoning(it) }
                is ContentBlock.Text ->
                    stripDirectives(block.text, committable = !(isStreaming && index == blocks.lastIndex))
                        .takeIf { it.isNotBlank() }?.let { ProcessItem.Narration(it) }
                is ContentBlock.ToolUse -> {
                    val result = toolResults[block.id]
                    ProcessItem.Tool(
                        name = block.name,
                        isComplete = result != null,
                        contextLabel = result
                            ?.takeIf { it.is_error != true }
                            ?.let { summarizeToolResult(block.name, it.content) },
                        isError = result?.is_error == true,
                    )
                }
                is ContentBlock.ToolResult -> null
            }
        }

        val response = blocks.drop(answerStart)
            .filterIsInstance<ContentBlock.Text>()
            .joinToString(separator = "\n\n") { it.text }
            .let(::stripDirectives)
            .let(::stripLeadingRule)
            .takeIf { it.isNotBlank() }

        // A do_nothing turn ends with no trailing text; fall back to the SUMMARY line, then the
        // do_nothing reason, so the card still explains the decision.
        val doNothingReason = blocks
            .filterIsInstance<ContentBlock.ToolUse>()
            .firstOrNull { it.name == "do_nothing" }
            ?.let { tool ->
                runCatching { tool.input.jsonObject["reason"]?.jsonPrimitive?.content }
                    .onFailure { Log.w(TAG, "read do_nothing reason failed", it) }
                    .getOrNull()
            }
            ?.takeIf { it.isNotBlank() }
        // While streaming, never let the SUMMARY (a pill label) or do_nothing reason stand in for the
        // answer — that flashed the summary in the response area before the real body streamed in. Only
        // fall back once settled (a do_nothing turn legitimately has no body).
        val answer = response ?: if (isStreaming) null else (summaryLine ?: doNothingReason)

        // Pill preview: gerund while working, past-tense summary once answered; falls back to the
        // first reasoning line if the model skipped the directives.
        val fallback = process
            .firstNotNullOfOrNull { (it as? ProcessItem.Reasoning)?.text ?: (it as? ProcessItem.Narration)?.text }
            ?.lineSequence()
            ?.firstOrNull { it.isNotBlank() }
            ?.take(80)
        val liveStatus = statuses.lastOrNull()
        val summary =
            if (answer != null) summaryLine ?: liveStatus ?: fallback
            else liveStatus ?: summaryLine ?: fallback

        return AiThreadUi(
            turnIndex = turnIndex,
            summary = summary,
            process = process,
            response = answer,
            durationSeconds = durationSeconds,
            isStreaming = isStreaming,
        )
    }

    internal fun decodeBlocks(json: String): List<ContentBlock> = runCatching {
        SessionJson.decodeFromString<List<ContentBlock>>(json)
    }
        .onFailure { Log.w(TAG, "decode AI content blocks failed", it) }
        .getOrElse { emptyList() }

    /** Drop a leading thematic break (e.g. "---") the model sometimes prefixes the answer with. */
    private fun stripLeadingRule(text: String): String {
        val lines = text.trimStart().lines()
        val first = lines.firstOrNull()?.trim().orEmpty()
        return if (first.matches(LEADING_RULE)) {
            lines.drop(1).joinToString("\n").trimStart()
        } else {
            text
        }
    }

    /** Condense a tool's JSON result into a one-line status label, or null if not worth showing. */
    private fun summarizeToolResult(name: String, content: String): String? = runCatching {
        val obj = SessionJson.parseToJsonElement(content).jsonObject
        when (name) {
            "read_calendar" -> {
                val n = obj["events"]?.jsonArray?.size ?: 0
                if (n == 1) "1 event" else "$n events"
            }
            "set_alarm" -> obj["alarm_time"]?.jsonPrimitive?.content?.let { iso ->
                val local = Instant.parse(iso).toLocalDateTime(TimeZone.currentSystemDefault())
                String.format(Locale.US, "set for %02d:%02d", local.hour, local.minute)
            }
            "cancel_alarm" -> "cancelled"
            "estimate_commute" -> obj["duration_sec"]?.jsonPrimitive?.content?.toLongOrNull()
                ?.let { "${it / 60} min" }
            "geocode_address" -> obj["formatted"]?.jsonPrimitive?.content?.take(28)
            else -> null
        }
    }.onFailure { Log.w(TAG, "summarize tool result failed for $name", it) }.getOrNull()

    private const val TAG = "AiThreadMapper"
}

/**
 * The index (into assistant-only blocks) where the answer begins. Anchored to the model's explicit
 * `SUMMARY:` marker — the last Text block carrying one is the start of the final answer; everything
 * before it is thinking-process. ("Last" so a stray `SUMMARY:` in earlier narration loses to the real
 * answer.) Stream content is append-only, so this index is stable across frames — no flash, no demotion.
 *
 * With no marker: while streaming, hold at the end (reveal nothing as the answer yet); once settled,
 * fall back to the trailing run of Text blocks so SUMMARY-less / do_nothing turns still surface a body.
 */
internal fun answerStartOf(blocks: List<ContentBlock>, isStreaming: Boolean): Int {
    val assistant = blocks.filterNot { it is ContentBlock.ToolResult }
    val summaryIndex = assistant.indexOfLast { it is ContentBlock.Text && it.text.hasSummaryLine() }
    return when {
        summaryIndex >= 0 -> summaryIndex
        isStreaming -> assistant.size
        else -> assistant.indexOfLast { it !is ContentBlock.Text } + 1
    }
}

/** True if any line of this text is a `SUMMARY:` directive — the model's "answer starts here" marker. */
private fun String.hasSummaryLine(): Boolean =
    lineSequence().any { SUMMARY_LINE.matchEntire(it.trim()) != null }

/** True if [line] is a strict prefix of a directive keyword still being typed (e.g. "STATU", "SUMMAR"),
 *  before its colon completes — so we can hold it back from display while streaming. */
private fun isPartialDirectivePrefix(line: String): Boolean =
    line.isNotEmpty() && DIRECTIVE_TOKENS.any { it.startsWith(line, ignoreCase = true) && line.length < it.length }

private val DIRECTIVE_TOKENS = listOf("STATUS:", "SUMMARY:")

// Hoisted so they compile once, not on every build() call.
private val LEADING_RULE = Regex("([-*_])\\1{2,}")
private val STATUS_LINE = Regex("(?i)^STATUS:\\s*(.*)$")
private val SUMMARY_LINE = Regex("(?i)^SUMMARY:\\s*(.*)$")
