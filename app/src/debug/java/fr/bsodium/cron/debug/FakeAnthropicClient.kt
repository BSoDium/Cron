package fr.bsodium.cron.debug

import android.util.Log
import fr.bsodium.cron.ai.AnthropicClient
import fr.bsodium.cron.ai.AnthropicMessages
import fr.bsodium.cron.ai.wire.ContentBlock
import fr.bsodium.cron.ai.wire.MessagesRequest
import fr.bsodium.cron.ai.wire.MessagesResponse
import fr.bsodium.cron.ai.wire.Usage
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonObject

/**
 * DEBUG-ONLY (debug source set). Replays scripted streaming events through the [AnthropicMessages]
 * seam, eliminating real API calls when iterating on streaming UI. Wired in via
 * [fr.bsodium.cron.ai.AnthropicClientFactory] when [MockApiPrefs.isEnabled] is true.
 *
 * Each scenario maps to a fixed sequence of turns; the [TurnRunner]'s tool-use loop advances the
 * turn counter normally, so tool calls still execute against the real tools (calendar, etc.).
 */
class FakeAnthropicClient(private val scenario: MockScenario) : AnthropicMessages {

    private var call = 0

    override suspend fun send(request: MessagesRequest): MessagesResponse =
        throw UnsupportedOperationException("FakeAnthropicClient supports the streaming path only")

    override suspend fun stream(
        request: MessagesRequest,
        onPartial: suspend (List<ContentBlock>) -> Unit,
    ): MessagesResponse {
        val turn = call++
        Log.i(TAG, "stream turn=$turn scenario=$scenario model=${request.model}")
        return when (scenario) {
            MockScenario.PLAN_SUCCESS -> planSuccess(request.model, onPartial)
            MockScenario.TOOL_CALL_ROUND_TRIP -> toolCallRoundTrip(turn, request.model, onPartial)
            MockScenario.STREAMING_ERROR -> streamingError(onPartial)
            MockScenario.EXTENDED_THINKING -> extendedThinking(request.model, onPartial)
        }
    }

    // ── Scenario implementations ───────────────────────────────────────────

    private suspend fun planSuccess(
        model: String,
        onPartial: suspend (List<ContentBlock>) -> Unit,
    ): MessagesResponse {
        val thinking = streamThinking(MOCK_REASONING, onPartial)
        val text = streamText(MOCK_ANSWER, listOf(thinking.signed()), onPartial)
        return response(model, listOf(thinking.signed(), text))
    }

    private suspend fun toolCallRoundTrip(
        turn: Int,
        model: String,
        onPartial: suspend (List<ContentBlock>) -> Unit,
    ): MessagesResponse = if (turn == 0) {
        val thinking = streamThinking(MOCK_REASONING, onPartial)
        val toolUse = ContentBlock.ToolUse(id = SIM_TOOL_ID, name = "read_calendar", input = JsonObject(emptyMap()))
        onPartial(listOf(thinking.signed(), toolUse))
        response(model, listOf(thinking.signed(), toolUse), stopReason = "tool_use")
    } else {
        // Subsequent calls: emit the scripted answer regardless of the real tool result.
        val text = streamText(MOCK_ANSWER, emptyList(), onPartial)
        response(model, listOf(text))
    }

    private suspend fun streamingError(
        onPartial: suspend (List<ContentBlock>) -> Unit,
    ): MessagesResponse {
        val words = MOCK_ANSWER.split(" ").take(3)
        val sb = StringBuilder()
        for (word in words) {
            if (sb.isNotEmpty()) sb.append(' ')
            sb.append(word)
            onPartial(listOf(ContentBlock.Text(sb.toString())))
            delay(WORD_DELAY_MS)
        }
        throw AnthropicClient.AnthropicHttpException(
            code = 529,
            type = "overloaded_error",
            message = "[mock] API overloaded — exercising retry path",
        )
    }

    private suspend fun extendedThinking(
        model: String,
        onPartial: suspend (List<ContentBlock>) -> Unit,
    ): MessagesResponse {
        val thinking = streamThinking(MOCK_REASONING_LONG, onPartial)
        val text = streamText(MOCK_ANSWER_LONG, listOf(thinking.signed()), onPartial)
        return response(model, listOf(thinking.signed(), text))
    }

    // ── Streaming primitives ───────────────────────────────────────────────

    private suspend fun streamThinking(
        text: String,
        onPartial: suspend (List<ContentBlock>) -> Unit,
    ): ContentBlock.Thinking {
        streamWords(text) { partial -> onPartial(listOf(ContentBlock.Thinking(thinking = partial))) }
        return ContentBlock.Thinking(thinking = text)
    }

    private suspend fun streamText(
        text: String,
        prefix: List<ContentBlock>,
        onPartial: suspend (List<ContentBlock>) -> Unit,
    ): ContentBlock.Text {
        streamWords(text) { partial -> onPartial(prefix + ContentBlock.Text(text = partial)) }
        return ContentBlock.Text(text = text)
    }

    private suspend fun streamWords(text: String, emit: suspend (String) -> Unit) {
        val words = text.split(" ")
        val sb = StringBuilder()
        words.forEachIndexed { i, word ->
            if (sb.isNotEmpty()) sb.append(' ')
            sb.append(word)
            emit(sb.toString())
            delay(if (i % 6 == 5) BURST_PAUSE_MS else WORD_DELAY_MS)
        }
    }

    /** Attaches the fake signature so TurnRunner can echo it on subsequent round-trips. */
    private fun ContentBlock.Thinking.signed() = copy(signature = SIM_SIGNATURE)

    private fun response(
        model: String,
        content: List<ContentBlock>,
        stopReason: String = "end_turn",
    ) = MessagesResponse(
        id = "sim-$call",
        model = model,
        role = "assistant",
        content = content,
        stop_reason = stopReason,
        usage = Usage(),
    )

    companion object {
        private const val TAG = "FakeAnthropicClient"
        private const val SIM_SIGNATURE = "sim-sig"
        private const val SIM_TOOL_ID = "sim-tool-1"
        private const val WORD_DELAY_MS = 20L
        private const val BURST_PAUSE_MS = 280L
    }
}
