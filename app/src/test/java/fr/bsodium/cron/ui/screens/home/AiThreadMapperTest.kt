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

    @Test
    fun streaming_leading_narration_is_not_shown_as_the_answer() {
        // A turn streams reasoning then prose narration before any tool call. The prose must stay in
        // the thinking process, never flash as the answer (it has no SUMMARY marker yet).
        val thread = AiThreadMapper.buildFromBlocks(
            turnIndex = 0,
            blocks = listOf(
                ContentBlock.Thinking(thinking = "Considering the morning."),
                ContentBlock.Text("I can see tomorrow's picture clearly — a packed morning."),
            ),
        )
        assertNull(thread.response)
        val narration = thread.process.filterIsInstance<ProcessItem.Narration>().single()
        assertEquals("I can see tomorrow's picture clearly — a packed morning.", narration.text)
    }

    @Test
    fun streaming_summary_line_alone_is_the_pill_not_the_response() {
        // The model has typed "SUMMARY: …" but not the answer body yet. The summary belongs in the pill;
        // the response area must stay empty (no flash) until the body streams in.
        val thread = AiThreadMapper.buildFromBlocks(
            turnIndex = 0,
            blocks = listOf(
                ContentBlock.Thinking(thinking = "Deciding."),
                ContentBlock.Text("SUMMARY: Set an 8:29 alarm so you have time"),
            ),
        )
        assertNull(thread.response)
        assertEquals("Set an 8:29 alarm so you have time", thread.summary)
    }

    @Test
    fun streaming_answer_is_revealed_once_marked_with_summary() {
        val thread = AiThreadMapper.buildFromBlocks(
            turnIndex = 0,
            blocks = listOf(
                ContentBlock.Thinking(thinking = "Considering the morning."),
                ContentBlock.Text("SUMMARY: Set your alarm\n\nSet a 6:40 alarm so you make stand-up."),
            ),
        )
        assertEquals("Set a 6:40 alarm so you make stand-up.", thread.response)
        assertEquals("Set your alarm", thread.summary)
    }

    private val answerThenTool = listOf(
        ContentBlock.Thinking(thinking = "Considering the morning."),
        ContentBlock.Text("SUMMARY: Set your alarm\n\nSet a 6:40 alarm so you make stand-up."),
        ContentBlock.ToolUse(id = "t1", name = "set_alarm", input = SessionJson.parseToJsonElement("{}")),
    )

    @Test
    fun a_tool_after_the_summary_answer_does_not_pull_it_back_into_thinking() {
        // The answer is anchored to the SUMMARY marker, so a trailing tool_use can't demote it back
        // into the thinking thread — the bug that the positional "trailing run" heuristic produced.
        val thread = AiThreadMapper.buildFromBlocks(turnIndex = 0, blocks = answerThenTool)
        assertEquals("Set a 6:40 alarm so you make stand-up.", thread.response)
        assertTrue(thread.process.filterIsInstance<ProcessItem.Narration>().isEmpty())
    }

    @Test
    fun the_last_summary_block_wins_over_a_stray_summary_in_earlier_narration() {
        val blocks = listOf(
            ContentBlock.Thinking(thinking = "Considering."),
            ContentBlock.Text("SUMMARY: misformatted early line\n\nsome narration"),
            ContentBlock.ToolUse(id = "t1", name = "read_calendar", input = SessionJson.parseToJsonElement("{}")),
            ContentBlock.Text("SUMMARY: Real decision\n\nThe real answer."),
        )
        val thread = AiThreadMapper.buildFromBlocks(turnIndex = 0, blocks = blocks)
        assertEquals("The real answer.", thread.response)
        assertEquals("Real decision", thread.summary)
        // The stray-SUMMARY block stays in the process as narration (its directive line stripped).
        val narration = thread.process.filterIsInstance<ProcessItem.Narration>().single()
        assertEquals("some narration", narration.text)
    }

    @Test
    fun answer_start_anchors_on_the_summary_marker() {
        val narrating = listOf(
            ContentBlock.Thinking(thinking = "Considering."),
            ContentBlock.Text("Looking at tomorrow."),
        )
        val answered = listOf(
            ContentBlock.Thinking(thinking = "Considering."),
            ContentBlock.Text("SUMMARY: x\n\nbody"),
        )
        // No SUMMARY yet while streaming: the boundary sits at the end, so nothing is the answer.
        assertEquals(2, answerStartOf(narrating, isStreaming = true))
        // SUMMARY present: the boundary anchors on the SUMMARY-bearing block.
        assertEquals(1, answerStartOf(answered, isStreaming = true))
        // Settled with no SUMMARY: fall back to the trailing run of Text blocks.
        assertEquals(1, answerStartOf(narrating, isStreaming = false))
    }
}
