package fr.bsodium.cron.ui.screens.home

import fr.bsodium.cron.ui.screens.home.components.revealThread
import fr.bsodium.cron.ui.screens.home.components.revealableLength
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StreamingRevealTest {

    private val thread = AiThreadUi(
        turnIndex = 0,
        summary = "Working",
        process = listOf(
            ProcessItem.Reasoning("abcde"),                              // 5 units
            ProcessItem.Tool(name = "read_calendar", isComplete = true), // 1 unit (TOOL_COST)
            ProcessItem.Narration("xyz"),                                // 3 units
        ),
        response = "hello",                                              // 5 units
        isStreaming = true,
    )

    @Test
    fun revealable_length_sums_process_text_tools_and_response() {
        assertEquals(5 + 1 + 3 + 5, thread.revealableLength())
    }

    @Test
    fun zero_budget_reveals_nothing() {
        val r = revealThread(thread, 0)
        assertEquals(emptyList<ProcessItem>(), r.process)
        assertNull(r.response)
        assertEquals("Working", r.summary) // the pill label is kept whole, not typewritten
    }

    @Test
    fun partial_budget_truncates_the_straddling_text_item() {
        val r = revealThread(thread, 3)
        assertEquals(listOf(ProcessItem.Reasoning("abc")), r.process)
        assertNull(r.response)
    }

    @Test
    fun a_tool_only_appears_once_the_budget_reaches_it() {
        // 5 reveals the reasoning exactly; the tool needs one more unit.
        assertEquals(listOf(ProcessItem.Reasoning("abcde")), revealThread(thread, 5).process)
        assertEquals(
            listOf(ProcessItem.Reasoning("abcde"), ProcessItem.Tool(name = "read_calendar", isComplete = true)),
            revealThread(thread, 6).process,
        )
    }

    @Test
    fun response_is_revealed_only_after_the_whole_process() {
        val r = revealThread(thread, 11) // 5 + 1 + 3 process, then 2 of the answer
        assertEquals(3, r.process.size)
        assertEquals("he", r.response)
    }

    @Test
    fun full_budget_returns_the_input_unchanged() {
        assertEquals(thread, revealThread(thread, thread.revealableLength()))
        // and over-budget is clamped to the same
        assertEquals(thread, revealThread(thread, 999))
    }
}
