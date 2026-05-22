package fr.bsodium.cron.ai

import fr.bsodium.cron.ai.wire.ContentBlock
import fr.bsodium.cron.ai.wire.MessageInput
import fr.bsodium.cron.ai.wire.MessagesRequest
import fr.bsodium.cron.ai.wire.MessagesResponse
import fr.bsodium.cron.ai.wire.ToolChoice
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
    private val client: AnthropicClient,
    private val aiMessageDao: AiMessageDao,
    private val model: String,
    private val systemPrompt: String,
    private val tools: ToolRegistry,
    private val maxRoundTrips: Int = 12,
    private val maxRetries: Int = 3,
    private val maxTokens: Int = 2048,
    private val toolChoice: ToolChoice? = null,
) {

    sealed class Outcome {
        data class Completed(val response: MessagesResponse) : Outcome()
        data class BudgetExhausted(val roundTrips: Int) : Outcome()
    }

    suspend fun run(
        sessionId: String,
        turnIndex: Int,
        initialUserMessage: String,
    ): Outcome {
        val messages = loadOrSeed(sessionId, turnIndex, initialUserMessage).toMutableList()

        repeat(maxRoundTrips) { round ->
            val request = MessagesRequest(
                model = model,
                max_tokens = maxTokens,
                system = systemPrompt,
                messages = messages.toList(),
                tools = tools.definitions,
                tool_choice = toolChoice,
            )

            val response = sendWithRetries(request)

            // Persist assistant message.
            val assistantBlocks = response.content
            persistMessage(sessionId, turnIndex, role = "assistant", blocks = assistantBlocks)
            messages.add(MessageInput(role = "assistant", content = assistantBlocks))

            val toolUses = assistantBlocks.filterIsInstance<ContentBlock.ToolUse>()
            if (toolUses.isEmpty() || response.stop_reason == "end_turn") {
                return Outcome.Completed(response)
            }

            // Execute every tool_use block emitted in this assistant message.
            val toolResults: List<ContentBlock> = toolUses.map { call ->
                executeToolCall(call)
            }

            persistMessage(sessionId, turnIndex, role = "user", blocks = toolResults)
            messages.add(MessageInput(role = "user", content = toolResults))
        }

        return Outcome.BudgetExhausted(maxRoundTrips)
    }

    private suspend fun executeToolCall(call: ContentBlock.ToolUse): ContentBlock.ToolResult {
        val tool = tools[call.name] ?: return ContentBlock.ToolResult(
            tool_use_id = call.id,
            content = """{"error":"unknown tool '${call.name}'"}""",
            is_error = true,
        )
        val result = runCatching { tool.execute(call.input) }.getOrElse { thrown ->
            return ContentBlock.ToolResult(
                tool_use_id = call.id,
                content = """{"error":"tool '${call.name}' threw: ${thrown.message?.take(200) ?: thrown::class.simpleName}"}""",
                is_error = true,
            )
        }
        return ContentBlock.ToolResult(
            tool_use_id = call.id,
            content = result.payload,
            is_error = result.isError.takeIf { it },
        )
    }

    private suspend fun sendWithRetries(request: MessagesRequest): MessagesResponse {
        var attempt = 0
        while (true) {
            try {
                return client.send(request)
            } catch (e: AnthropicClient.AnthropicHttpException) {
                if (!e.isRetryable || attempt >= maxRetries) throw e
                val backoffMs = min(BASE_BACKOFF_MS shl attempt, MAX_BACKOFF_MS)
                delay(backoffMs)
                attempt++
            }
        }
    }

    private suspend fun loadOrSeed(
        sessionId: String,
        turnIndex: Int,
        initialUserMessage: String,
    ): List<MessageInput> {
        val persisted = aiMessageDao.findByTurn(sessionId, turnIndex)
        if (persisted.isNotEmpty()) {
            return persisted.map { row ->
                MessageInput(
                    role = row.role,
                    content = SessionJson.decodeFromString<List<ContentBlock>>(row.contentJson),
                )
            }
        }

        // Seed with the initial user message.
        val seedBlocks = listOf(ContentBlock.Text(initialUserMessage))
        persistMessage(sessionId, turnIndex, role = "user", blocks = seedBlocks)
        return listOf(MessageInput(role = "user", content = seedBlocks))
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
