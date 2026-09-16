package fr.bsodium.cron.ai

import fr.bsodium.cron.ai.wire.ContentBlock
import fr.bsodium.cron.ai.wire.MessageInput
import fr.bsodium.cron.ai.wire.MessagesRequest
import fr.bsodium.cron.ai.wire.MessagesResponse
import kotlinx.coroutines.delay
import kotlin.math.min

/**
 * Drives a single, session-independent AI tool-use loop for a memory-mutation turn: given a
 * [prompt] (the current memory list plus the user's typed instruction), calls the model, dispatches
 * any tool_use blocks, and repeats until the model stops calling tools or the round-trip budget is
 * spent.
 *
 * Deliberately NOT a variant of [TurnRunner]: there's no session/turn to persist against (no
 * `AiMessageEntity` FK to satisfy), no live transcript UI to stream partial deltas into, and no
 * resume-on-crash need — if the process dies mid-turn, the user just retypes, and any tool calls
 * that already landed are already durable in the memory table regardless.
 */
class MemoryTurnRunner(
    private val client: AnthropicMessages,
    private val model: String,
    private val systemPrompt: String,
    private val tools: ToolRegistry,
    private val maxRoundTrips: Int = 4,
    private val maxRetries: Int = 3,
    private val maxTokens: Int = 1024,
) {

    sealed class Outcome {
        data class Completed(val response: MessagesResponse) : Outcome()
        data class BudgetExhausted(val roundTrips: Int) : Outcome()
    }

    suspend fun run(prompt: String): Outcome {
        val messages = mutableListOf(MessageInput(role = "user", content = listOf(ContentBlock.Text(prompt))))

        repeat(maxRoundTrips) {
            val request = MessagesRequest(
                model = model,
                max_tokens = maxTokens,
                system = systemPrompt,
                messages = messages.toList(),
                tools = tools.definitions,
            )
            val response = sendWithRetries(request)
            messages.add(MessageInput(role = "assistant", content = response.content))

            val toolUses = response.content.filterIsInstance<ContentBlock.ToolUse>()
            if (toolUses.isEmpty() || response.stop_reason == "end_turn") {
                return Outcome.Completed(response)
            }

            val toolResults: List<ContentBlock> = toolUses.map { call -> executeToolCall(tools, call) }
            messages.add(MessageInput(role = "user", content = toolResults))
        }

        return Outcome.BudgetExhausted(maxRoundTrips)
    }

    private suspend fun sendWithRetries(request: MessagesRequest): MessagesResponse {
        var attempt = 0
        while (true) {
            try {
                return client.send(request)
            } catch (e: AnthropicClient.AnthropicHttpException) {
                if (!e.isRetryable || attempt >= maxRetries) throw e
                delay(min(BASE_BACKOFF_MS shl attempt, MAX_BACKOFF_MS))
                attempt++
            }
        }
    }

    private companion object {
        const val BASE_BACKOFF_MS = 1_000L
        const val MAX_BACKOFF_MS = 30_000L
    }
}
