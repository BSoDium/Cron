package fr.bsodium.cron.ui.screens.home

import fr.bsodium.cron.ai.wire.ContentBlock
import fr.bsodium.cron.session.db.AiMessageEntity
import fr.bsodium.cron.session.db.SessionJson
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiThreadMapperTest {

    private fun row(turn: Int, role: String, vararg blocks: ContentBlock) = AiMessageEntity(
        sessionId = "s",
        turnIndex = turn,
        role = role,
        contentJson = SessionJson.encodeToString<List<ContentBlock>>(blocks.toList()),
        createdAt = 0L,
    )

    @Test
    fun empty_rows_produce_null() {
        assertNull(AiThreadMapper.build(emptyList()))
    }

    @Test
    fun a_turn_with_no_assistant_blocks_shows_the_thinking_placeholder() {
        val thread = AiThreadMapper.build(listOf(row(0, "user", ContentBlock.Text("plan it"))))
        assertEquals("Thinking…", thread?.summary)
        assertTrue(thread?.process.orEmpty().isEmpty())
        assertNull(thread?.response)
    }

    @Test
    fun parses_reasoning_and_tool_steps_and_strips_the_summary_directive() {
        val rows = listOf(
            row(0, "user", ContentBlock.Text("plan it")),
            row(
                0,
                "assistant",
                ContentBlock.Thinking(thinking = "Reading the calendar first."),
                ContentBlock.ToolUse(id = "t1", name = "set_alarm", input = SessionJson.parseToJsonElement("{}")),
                ContentBlock.Text("SUMMARY: Set your alarm\n\nSet a 6:40 alarm so you make stand-up."),
            ),
            row(
                0,
                "user",
                ContentBlock.ToolResult(
                    tool_use_id = "t1",
                    content = "{\"alarm_time\":\"2026-05-22T06:40:00Z\"}",
                    is_error = false,
                ),
            ),
        )
        val thread = requireNotNull(AiThreadMapper.build(rows))

        // The SUMMARY directive becomes the pill summary and is stripped out of the rendered body.
        assertEquals("Set your alarm", thread.summary)
        val response = requireNotNull(thread.response)
        assertEquals("Set a 6:40 alarm so you make stand-up.", response)
        assertFalse(response.contains("SUMMARY"))

        val reasoning = thread.process.filterIsInstance<ProcessItem.Reasoning>().single()
        assertEquals("Reading the calendar first.", reasoning.text)

        val tool = thread.process.filterIsInstance<ProcessItem.Tool>().single()
        assertEquals("set_alarm", tool.name)
        assertTrue(tool.isComplete)
        assertFalse(tool.isError)
    }

    @Test
    fun streaming_blocks_render_identically_to_the_settled_rows() {
        val thinking = ContentBlock.Thinking(thinking = "Reading the calendar first.")
        val toolUse = ContentBlock.ToolUse(id = "t1", name = "set_alarm", input = SessionJson.parseToJsonElement("{}"))
        val toolResult = ContentBlock.ToolResult(
            tool_use_id = "t1",
            content = "{\"alarm_time\":\"2026-05-22T06:40:00Z\"}",
            is_error = false,
        )
        val answer = ContentBlock.Text("SUMMARY: Set your alarm\n\nSet a 6:40 alarm so you make stand-up.")

        val settled = requireNotNull(
            AiThreadMapper.build(
                listOf(
                    row(0, "user", ContentBlock.Text("plan it")),
                    row(0, "assistant", thinking, toolUse, answer),
                    row(0, "user", toolResult),
                ),
            ),
        )
        // committedBlocks order during streaming: assistant blocks, then the tool_result, then the answer.
        val streamed = AiThreadMapper.buildFromBlocks(
            turnIndex = 0,
            blocks = listOf(thinking, toolUse, toolResult, answer),
        )

        assertTrue(streamed.isStreaming)
        // Same summary/process/response — only the settled-only duration and the streaming flag differ.
        assertEquals(settled.copy(durationSeconds = null, isStreaming = true), streamed)
    }

    @Test
    fun a_streaming_turn_with_no_assistant_blocks_yet_shows_the_placeholder() {
        val thread = AiThreadMapper.buildFromBlocks(turnIndex = 0, blocks = emptyList())
        assertEquals("Thinking…", thread.summary)
        assertTrue(thread.process.isEmpty())
        assertNull(thread.response)
        assertTrue(thread.isStreaming)
    }
}
