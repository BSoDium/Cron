package fr.bsodium.cron.ai

import fr.bsodium.cron.ai.wire.ContentBlock
import fr.bsodium.cron.ai.wire.MessageInput
import fr.bsodium.cron.ai.wire.MessagesRequest
import fr.bsodium.cron.ai.wire.MessagesResponse
import fr.bsodium.cron.ai.wire.ThinkingConfig
import fr.bsodium.cron.ai.wire.ToolChoice
import fr.bsodium.cron.ai.wire.Usage
import fr.bsodium.cron.session.db.AiMessageDao
import fr.bsodium.cron.session.db.AiMessageEntity
import fr.bsodium.cron.session.db.SessionJson
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlinx.serialization.encodeToString
import kotlin.math.min

/**
 * Drives a single AI tool-use loop for one [sessionId] + [turnIndex] pair.
 *
 * Behaviour:
 *  1. Reads any persisted messages for the (session, turn) — supports resume.
 *  2. Sends them to Anthropic via [client].
 *  3. For each tool_use block in the response, runs the tool and appends the
 *     tool_result block back to the conversation. Persists both before the
 *     next API call.
 *  4. Stops when the model emits `stop_reason = end_turn` or the per-turn
 *     round-trip budget is exhausted.
 *
 * Retries on 429 / 5xx with exponential backoff, capped at [maxRetries].
 */
class TurnRunner(
    private val client: AnthropicMessages,
    private val aiMessageDao: AiMessageDao,
    private val model: String,
    private val systemPrompt: String,
    private val tools: ToolRegistry,
    private val maxRoundTrips: Int = 12,
    private val maxRetries: Int = 3,
    private val maxTokens: Int = 2048,
    private val toolChoice: ToolChoice? = null,
    private val thinking: ThinkingConfig? = null,
) {

    sealed class Outcome {
        /** Aggregated token usage across every round-trip in the turn. */
        abstract val totalUsage: Usage

        data class Completed(
            val response: MessagesResponse,
            override val totalUsage: Usage,
        ) : Outcome()

        data class BudgetExhausted(
            val roundTrips: Int,
            override val totalUsage: Usage,
        ) : Outcome()
    }

    suspend fun run(
        sessionId: String,
        turnIndex: Int,
        initialUserMessage: String,
    ): Outcome {
        val startedAtMs = Clock.System.now().toEpochMilliseconds()
        val (loaded, isFreshSeed) = loadOrSeed(sessionId, turnIndex, initialUserMessage)
        val messages = loaded.toMutableList()
        // The turn's renderable blocks so far (assistant content + tool_result, never the user prompt),
        // seeded from any already-persisted messages on resume. Streamed deltas append onto this.
        var committedBlocks = renderableBlocks(messages)

        // Mark the turn streaming BEFORE its seed row is persisted, so the home thread renders "thinking"
        // from the first frame instead of briefly showing the bare user row as a settled turn ("Thought
        // for a moment"). Skip if something already seeded this turn (the FAB path's seedPending carries a
        // trigger label we mustn't clobber). The finally-clear ends the streaming state on completion.
        val alreadySeeded = StreamingTurnStore.active.value
            ?.let { it.sessionId == sessionId && it.turnIndex == turnIndex } == true
        if (!alreadySeeded) publish(sessionId, turnIndex, committedBlocks, startedAtMs)
        if (isFreshSeed) persistMessage(sessionId, turnIndex, role = "user", blocks = messages.first().content)

        var aggregateUsage = Usage()

        return try {
            repeat(maxRoundTrips) { round ->
                val request = MessagesRequest(
                    model = model,
                    max_tokens = maxTokens,
                    system = systemPrompt,
                    messages = messages.toList(),
                    tools = tools.definitions,
                    tool_choice = toolChoice,
                    thinking = thinking,
                )

                val response = streamWithRetries(sessionId, turnIndex, request, committedBlocks, startedAtMs)
                aggregateUsage = aggregateUsage.plus(response.usage)

                // Persist assistant message (complete — keeps resume safe), then settle the partial.
                val assistantBlocks = response.content
                persistMessage(sessionId, turnIndex, role = "assistant", blocks = assistantBlocks)
                messages.add(MessageInput(role = "assistant", content = assistantBlocks))
                committedBlocks = committedBlocks + assistantBlocks
                publish(sessionId, turnIndex, committedBlocks, startedAtMs)

                val toolUses = assistantBlocks.filterIsInstance<ContentBlock.ToolUse>()
                if (toolUses.isEmpty() || response.stop_reason == "end_turn") {
                    return Outcome.Completed(response, aggregateUsage)
                }

                // Execute every tool_use block emitted in this assistant message.
                val toolResults: List<ContentBlock> = toolUses.map { call ->
                    executeToolCall(call)
                }

                persistMessage(sessionId, turnIndex, role = "user", blocks = toolResults)
                messages.add(MessageInput(role = "user", content = toolResults))
                committedBlocks = committedBlocks + toolResults
                publish(sessionId, turnIndex, committedBlocks, startedAtMs)
            }

            Outcome.BudgetExhausted(maxRoundTrips, aggregateUsage)
        } finally {
            StreamingTurnStore.clear(sessionId, turnIndex)
        }
    }

    private fun publish(sessionId: String, turnIndex: Int, blocks: List<ContentBlock>, startedAtMs: Long) =
        StreamingTurnStore.update(StreamingTurn(sessionId, turnIndex, blocks, startedAtMs))

    /** Blocks the home thread renders: assistant content plus tool_result, excluding the user prompt. */
    private fun renderableBlocks(messages: List<MessageInput>): List<ContentBlock> = buildList {
        messages.forEach { message ->
            if (message.role == "assistant") addAll(message.content)
            else addAll(message.content.filterIsInstance<ContentBlock.ToolResult>())
        }
    }

    private fun Usage.plus(other: Usage?): Usage {
        if (other == null) return this
        return Usage(
            input_tokens = input_tokens + other.input_tokens,
            output_tokens = output_tokens + other.output_tokens,
            cache_creation_input_tokens = cache_creation_input_tokens + other.cache_creation_input_tokens,
            cache_read_input_tokens = cache_read_input_tokens + other.cache_read_input_tokens,
        )
    }

    private suspend fun executeToolCall(call: ContentBlock.ToolUse): ContentBlock.ToolResult {
        val tool = tools[call.name] ?: return ContentBlock.ToolResult(
            tool_use_id = call.id,
            content = toolErrorResult("unknown tool '${call.name}'").payload,
            is_error = true,
        )
        val result = runCatching { tool.execute(call.input) }.getOrElse { thrown ->
            return ContentBlock.ToolResult(
                tool_use_id = call.id,
                content = toolErrorResult(
                    "tool '${call.name}' threw: ${thrown.message?.take(200) ?: thrown::class.simpleName}"
                ).payload,
                is_error = true,
            )
        }
        return ContentBlock.ToolResult(
            tool_use_id = call.id,
            content = result.payload,
            is_error = result.isError.takeIf { it },
        )
    }

    private suspend fun streamWithRetries(
        sessionId: String,
        turnIndex: Int,
        request: MessagesRequest,
        committedBlocks: List<ContentBlock>,
        startedAtMs: Long,
    ): MessagesResponse {
        var attempt = 0
        while (true) {
            try {
                return client.stream(request) { streamingBlocks ->
                    publish(sessionId, turnIndex, committedBlocks + streamingBlocks, startedAtMs)
                }
            } catch (e: AnthropicClient.AnthropicHttpException) {
                if (!e.isRetryable || attempt >= maxRetries) throw e
                // A fresh attempt re-streams from scratch — drop the half-streamed tail so the UI
                // doesn't briefly show doubled text.
                publish(sessionId, turnIndex, committedBlocks, startedAtMs)
                val backoffMs = min(BASE_BACKOFF_MS shl attempt, MAX_BACKOFF_MS)
                delay(backoffMs)
                attempt++
            }
        }
    }

    /**
     * Loads the persisted messages for the turn, or builds the initial user seed in memory. Returns the
     * messages and whether a fresh seed still needs persisting — the caller persists it only *after*
     * marking the turn streaming, so the seed row never renders as a settled turn before the first token.
     */
    private suspend fun loadOrSeed(
        sessionId: String,
        turnIndex: Int,
        initialUserMessage: String,
    ): Pair<List<MessageInput>, Boolean> {
        val persisted = aiMessageDao.findByTurn(sessionId, turnIndex)
        if (persisted.isNotEmpty()) {
            val messages = persisted.map { row ->
                MessageInput(
                    role = row.role,
                    content = SessionJson.decodeFromString<List<ContentBlock>>(row.contentJson),
                )
            }
            return messages to false
        }

        val seedBlocks = listOf(ContentBlock.Text(initialUserMessage))
        return listOf(MessageInput(role = "user", content = seedBlocks)) to true
    }

    private suspend fun persistMessage(
        sessionId: String,
        turnIndex: Int,
        role: String,
        blocks: List<ContentBlock>,
    ) {
        aiMessageDao.insert(
            AiMessageEntity(
                sessionId = sessionId,
                turnIndex = turnIndex,
                role = role,
                contentJson = SessionJson.encodeToString(blocks),
                createdAt = Clock.System.now().toEpochMilliseconds(),
            )
        )
    }

    companion object {
        const val MODEL_HAIKU = "claude-haiku-4-5"
        const val MODEL_SONNET = "claude-sonnet-4-6"
        private const val BASE_BACKOFF_MS = 1_000L
        private const val MAX_BACKOFF_MS = 30_000L
    }
}
