package fr.bsodium.cron.ai

import fr.bsodium.cron.ai.wire.ContentBlock
import fr.bsodium.cron.ai.wire.Delta
import fr.bsodium.cron.ai.wire.MessagesResponse
import fr.bsodium.cron.ai.wire.StreamEvent
import fr.bsodium.cron.ai.wire.Usage
import fr.bsodium.cron.session.db.SessionJson
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Reassembles a streamed Anthropic message from [StreamEvent]s into a [MessagesResponse],
 * so a streaming turn hands the [TurnRunner] the same shape a blocking `send()` would.
 *
 * Pure and non-suspending — no OkHttp, so it's unit-testable in isolation. One instance per
 * round-trip (a retry constructs a fresh accumulator, never replaying prior deltas).
 */
class StreamAccumulator {

    private val builders = mutableListOf<BlockBuilder>()
    private var messageId: String? = null
    private var role: String? = null
    private var streamModel: String? = null
    private var stopReason: String? = null
    private var usage = Usage()

    /** Feeds one event into the running state; returns true once the stream is terminal. */
    fun apply(event: StreamEvent): Boolean = when (event) {
        is StreamEvent.MessageStart -> {
            messageId = event.message.id
            role = event.message.role
            streamModel = event.message.model
            event.message.usage?.let { usage = it }
            builders.clear()
            false
        }
        is StreamEvent.ContentBlockStart -> {
            setBuilder(event.index, BlockBuilder.from(event.content_block))
            false
        }
        is StreamEvent.ContentBlockDelta -> {
            builders.getOrNull(event.index)?.apply(event.delta)
            false
        }
        is StreamEvent.ContentBlockStop -> false
        is StreamEvent.MessageDelta -> {
            event.delta.stop_reason?.let { stopReason = it }
            event.usage?.let { usage = usage.copy(output_tokens = it.output_tokens) }
            false
        }
        is StreamEvent.MessageStop -> true
        is StreamEvent.Ping -> false
        // Mid-stream errors (e.g. overloaded_error) arrive after a 200; map to 529 so the
        // runner's retry wrapper treats them as retryable, just like a pre-stream 529.
        is StreamEvent.Error -> throw AnthropicClient.AnthropicHttpException(
            code = 529,
            type = event.error.type,
            message = event.error.message,
        )
    }

    /** Blocks accumulated so far, for live UI rendering. Tool input is parsed leniently. */
    fun snapshotBlocks(): List<ContentBlock> = builders.mapNotNull { it.toContentBlock(strict = false) }

    /**
     * The finished message. Throws a retryable [AnthropicClient.AnthropicHttpException] if the
     * stream ended before a `stop_reason` (truncated socket) — the round-trip is then retried.
     */
    fun toResponse(fallbackModel: String): MessagesResponse {
        val reason = stopReason ?: throw AnthropicClient.AnthropicHttpException(
            code = 503,
            type = "incomplete_stream",
            message = "stream ended before stop_reason",
        )
        return MessagesResponse(
            id = messageId.orEmpty(),
            model = streamModel ?: fallbackModel,
            role = role ?: "assistant",
            content = builders.mapNotNull { it.toContentBlock(strict = true) },
            stop_reason = reason,
            usage = usage,
        )
    }

    private fun setBuilder(index: Int, builder: BlockBuilder) {
        while (builders.size <= index) builders.add(BlockBuilder("text"))
        builders[index] = builder
    }
}

/** Mutable per-index scratch for one content block as its deltas arrive. */
private class BlockBuilder(private val type: String) {
    private val text = StringBuilder()
    private val partialJson = StringBuilder()
    private var toolId: String? = null
    private var toolName: String? = null
    private var initialInput: JsonElement? = null
    private var signature: String? = null

    fun apply(delta: Delta) {
        when (delta) {
            is Delta.TextDelta -> text.append(delta.text)
            is Delta.ThinkingDelta -> text.append(delta.thinking)
            is Delta.InputJsonDelta -> partialJson.append(delta.partial_json)
            is Delta.SignatureDelta -> signature = delta.signature
        }
    }

    /** [strict] = the final pass: a tool_use whose accumulated input won't parse is a corrupt stream. */
    fun toContentBlock(strict: Boolean): ContentBlock? = when (type) {
        "text" -> ContentBlock.Text(text.toString())
        "thinking" -> ContentBlock.Thinking(thinking = text.toString(), signature = signature)
        "tool_use" -> buildToolUse(strict)
        else -> null // tool_result is never model-emitted; unknown future types are skipped.
    }

    private fun buildToolUse(strict: Boolean): ContentBlock.ToolUse? {
        val id = toolId
        val name = toolName
        if (id == null || name == null) {
            if (strict) throw AnthropicClient.AnthropicHttpException(503, "incomplete_stream", "tool_use missing id/name")
            return null
        }
        return ContentBlock.ToolUse(id = id, name = name, input = resolveInput(strict))
    }

    private fun resolveInput(strict: Boolean): JsonElement {
        val raw = partialJson.toString()
        if (raw.isBlank()) return initialInput ?: EMPTY_OBJECT
        return runCatching { SessionJson.parseToJsonElement(raw) }.getOrElse {
            if (strict) throw AnthropicClient.AnthropicHttpException(503, "incomplete_stream", "tool_use input not valid JSON")
            initialInput ?: EMPTY_OBJECT
        }
    }

    companion object {
        private val EMPTY_OBJECT = JsonObject(emptyMap())

        fun from(block: ContentBlock): BlockBuilder = when (block) {
            is ContentBlock.Text -> BlockBuilder("text").also { it.text.append(block.text) }
            is ContentBlock.Thinking -> BlockBuilder("thinking").also {
                it.text.append(block.thinking)
                it.signature = block.signature
            }
            is ContentBlock.ToolUse -> BlockBuilder("tool_use").also {
                it.toolId = block.id
                it.toolName = block.name
                it.initialInput = block.input
            }
            is ContentBlock.ToolResult -> BlockBuilder("tool_result")
        }
    }
}
