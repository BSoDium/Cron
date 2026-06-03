package fr.bsodium.cron.ai

import fr.bsodium.cron.ai.wire.ContentBlock
import fr.bsodium.cron.ai.wire.Delta
import fr.bsodium.cron.ai.wire.MessageDeltaInfo
import fr.bsodium.cron.ai.wire.MessageStartInfo
import fr.bsodium.cron.ai.wire.StreamEvent
import fr.bsodium.cron.ai.wire.Usage
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamAccumulatorTest {

    private fun start(usageInput: Int = 7) = StreamEvent.MessageStart(
        MessageStartInfo(id = "msg_1", model = "claude-haiku-4-5", role = "assistant", usage = Usage(input_tokens = usageInput, output_tokens = 1)),
    )

    private fun blockStart(index: Int, block: ContentBlock) = StreamEvent.ContentBlockStart(index, block)
    private fun delta(index: Int, d: Delta) = StreamEvent.ContentBlockDelta(index, d)
    private fun stopMessage(reason: String = "end_turn", out: Int = 5) =
        StreamEvent.MessageDelta(MessageDeltaInfo(stop_reason = reason), Usage(output_tokens = out))

    @Test
    fun text_turn_reassembles_into_one_text_block() {
        val acc = StreamAccumulator()
        acc.apply(start())
        acc.apply(blockStart(0, ContentBlock.Text("")))
        acc.apply(delta(0, Delta.TextDelta("Hel")))
        acc.apply(delta(0, Delta.TextDelta("lo")))
        acc.apply(StreamEvent.ContentBlockStop(0))
        acc.apply(stopMessage(out = 12))
        assertTrue(acc.apply(StreamEvent.MessageStop))

        val response = acc.toResponse(fallbackModel = "fallback")
        assertEquals("msg_1", response.id)
        assertEquals("assistant", response.role)
        assertEquals("claude-haiku-4-5", response.model)
        assertEquals("end_turn", response.stop_reason)
        assertEquals(listOf(ContentBlock.Text("Hello")), response.content)
        assertEquals(7, response.usage?.input_tokens)
        assertEquals(12, response.usage?.output_tokens)
    }

    @Test
    fun thinking_signature_is_preserved() {
        val acc = StreamAccumulator()
        acc.apply(start())
        acc.apply(blockStart(0, ContentBlock.Thinking(thinking = "")))
        acc.apply(delta(0, Delta.ThinkingDelta("rea")))
        acc.apply(delta(0, Delta.ThinkingDelta("soning")))
        acc.apply(delta(0, Delta.SignatureDelta("sig-abc")))
        acc.apply(stopMessage())
        acc.apply(StreamEvent.MessageStop)

        val thinking = acc.toResponse("m").content.single() as ContentBlock.Thinking
        assertEquals("reasoning", thinking.thinking)
        assertEquals("sig-abc", thinking.signature)
    }

    @Test
    fun tool_use_input_accumulates_across_fragments() {
        val acc = StreamAccumulator()
        acc.apply(start())
        acc.apply(blockStart(0, ContentBlock.ToolUse(id = "toolu_1", name = "set_alarm", input = JsonObject(emptyMap()))))
        acc.apply(delta(0, Delta.InputJsonDelta("""{"time""")))

        // Mid-stream the input is not yet valid JSON → lenient snapshot yields an empty object, never a throw.
        val partial = acc.snapshotBlocks().single() as ContentBlock.ToolUse
        assertEquals("toolu_1", partial.id)
        assertTrue((partial.input as JsonObject).isEmpty())

        acc.apply(delta(0, Delta.InputJsonDelta("""":"06:40"}""")))
        acc.apply(stopMessage())
        acc.apply(StreamEvent.MessageStop)

        val tool = acc.toResponse("m").content.single() as ContentBlock.ToolUse
        assertEquals("set_alarm", tool.name)
        assertEquals("06:40", (tool.input as JsonObject)["time"]?.jsonPrimitive?.content)
    }

    @Test
    fun mid_stream_error_throws_retryable() {
        val acc = StreamAccumulator()
        acc.apply(start())
        val ex = assertThrows(AnthropicClient.AnthropicHttpException::class.java) {
            acc.apply(StreamEvent.Error(fr.bsodium.cron.ai.wire.ApiError(type = "overloaded_error", message = "Overloaded")))
        }
        assertEquals(529, ex.code)
        assertTrue(ex.isRetryable)
    }

    @Test
    fun truncated_stream_without_stop_reason_throws_retryable() {
        val acc = StreamAccumulator()
        acc.apply(start())
        acc.apply(blockStart(0, ContentBlock.Text("")))
        acc.apply(delta(0, Delta.TextDelta("partial")))
        // socket closed: no message_delta / message_stop
        val ex = assertThrows(AnthropicClient.AnthropicHttpException::class.java) {
            acc.toResponse(fallbackModel = "m")
        }
        assertEquals(503, ex.code)
        assertTrue(ex.isRetryable)
    }
}
