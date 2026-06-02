package fr.bsodium.cron.ai.wire

import fr.bsodium.cron.session.db.SessionJson
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Decodes representative `data:` payloads to prove the `"type"` discriminators resolve and the
 *  load-bearing fields (tool id/name, partial_json, signature, stop_reason) survive. */
class AnthropicStreamTest {

    private inline fun <reified T> decode(json: String): T = SessionJson.decodeFromString<T>(json)

    @Test
    fun message_start_carries_id_role_usage() {
        val event = decode<StreamEvent>(
            """{"type":"message_start","message":{"id":"msg_1","model":"claude-haiku-4-5","role":"assistant","usage":{"input_tokens":10,"output_tokens":1}}}""",
        )
        event as StreamEvent.MessageStart
        assertEquals("msg_1", event.message.id)
        assertEquals("assistant", event.message.role)
        assertEquals(10, event.message.usage?.input_tokens)
    }

    @Test
    fun content_block_start_decodes_tool_use_id_and_name() {
        val event = decode<StreamEvent>(
            """{"type":"content_block_start","index":1,"content_block":{"type":"tool_use","id":"toolu_1","name":"set_alarm","input":{}}}""",
        )
        event as StreamEvent.ContentBlockStart
        assertEquals(1, event.index)
        val block = event.content_block as ContentBlock.ToolUse
        assertEquals("toolu_1", block.id)
        assertEquals("set_alarm", block.name)
    }

    @Test
    fun every_delta_variant_resolves() {
        assertEquals("Hi", (delta("""{"type":"text_delta","text":"Hi"}""") as Delta.TextDelta).text)
        assertEquals("hmm", (delta("""{"type":"thinking_delta","thinking":"hmm"}""") as Delta.ThinkingDelta).thinking)
        assertEquals("""{"x":1}""", (delta("""{"type":"input_json_delta","partial_json":"{\"x\":1}"}""") as Delta.InputJsonDelta).partial_json)
        assertEquals("sig==", (delta("""{"type":"signature_delta","signature":"sig=="}""") as Delta.SignatureDelta).signature)
    }

    private fun delta(json: String): Delta =
        (decode<StreamEvent>("""{"type":"content_block_delta","index":0,"delta":$json}""") as StreamEvent.ContentBlockDelta).delta

    @Test
    fun message_delta_carries_stop_reason_and_output_tokens() {
        val event = decode<StreamEvent>(
            """{"type":"message_delta","delta":{"stop_reason":"end_turn","stop_sequence":null},"usage":{"output_tokens":42}}""",
        )
        event as StreamEvent.MessageDelta
        assertEquals("end_turn", event.delta.stop_reason)
        assertEquals(42, event.usage?.output_tokens)
    }

    @Test
    fun terminal_and_control_events_decode_as_objects() {
        assertTrue(decode<StreamEvent>("""{"type":"message_stop"}""") is StreamEvent.MessageStop)
        assertTrue(decode<StreamEvent>("""{"type":"ping"}""") is StreamEvent.Ping)
    }

    @Test
    fun error_event_carries_type_and_message() {
        val event = decode<StreamEvent>(
            """{"type":"error","error":{"type":"overloaded_error","message":"Overloaded"}}""",
        )
        event as StreamEvent.Error
        assertEquals("overloaded_error", event.error.type)
        assertEquals("Overloaded", event.error.message)
    }

    @Test
    fun unknown_keys_in_content_block_start_are_tolerated() {
        // message_start sends a full message object with extra fields (content, stop_reason …).
        val event = decode<StreamEvent>(
            """{"type":"content_block_start","index":0,"content_block":{"type":"text","text":""},"extra":"ignored"}""",
        )
        assertTrue(event is StreamEvent.ContentBlockStart)
    }

    @Test
    fun input_json_partial_is_raw_string_not_reparsed() {
        // The fragment is an incomplete JSON string; it must round-trip verbatim, not as an object.
        val d = delta("""{"type":"input_json_delta","partial_json":"{\"address\":\"1 Infi"}""") as Delta.InputJsonDelta
        assertEquals("""{"address":"1 Infi""", d.partial_json)
        // sanity: it is genuinely not yet valid JSON
        assertTrue(runCatching { SessionJson.parseToJsonElement(d.partial_json).jsonObject["address"]?.jsonPrimitive }.isFailure)
    }
}
