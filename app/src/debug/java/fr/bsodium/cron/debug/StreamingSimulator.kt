package fr.bsodium.cron.debug

import fr.bsodium.cron.ai.StreamingTurn
import fr.bsodium.cron.ai.StreamingTurnStore
import fr.bsodium.cron.ai.wire.ContentBlock
import fr.bsodium.cron.ui.screens.home.AiThreadMapper
import fr.bsodium.cron.ui.screens.home.AiThreadUi
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonObject

/**
 * DEBUG-ONLY (debug source set). Scripts a fake streaming turn through the real [StreamingTurnStore]
 * and [AiThreadMapper] so the streaming UI — token render, trailing fade, narration-vs-answer
 * classification, and store-sourced haptics — can be exercised on-device or in a preview WITHOUT an
 * Anthropic API call. Production (`main/`) code is untouched.
 *
 * The script deliberately interleaves a Thinking block, free narration prose (no SUMMARY — must stay
 * in the thinking timeline, never flash as the answer), a tool round-trip, and finally a
 * SUMMARY-marked answer. Word delays are uneven to mimic the model's bursty delivery so the haptic
 * spread is realistic.
 */
object StreamingSimulator {

    private const val SESSION_ID = "stream-test"
    private const val WORD_DELAY_MS = 40L
    private const val BURST_PAUSE_MS = 280L
    private const val PHASE_PAUSE_MS = 500L

    suspend fun run(onThread: (AiThreadUi) -> Unit) {
        var committed = listOf<ContentBlock>()

        fun publish(current: List<ContentBlock>) {
            val blocks = committed + current
            StreamingTurnStore.update(StreamingTurn(SESSION_ID, turnIndex = 0, blocks = blocks, startedAtMs = 0L))
            onThread(AiThreadMapper.buildFromBlocks(turnIndex = 0, blocks = blocks))
        }

        suspend fun streamWords(full: String, build: (String) -> ContentBlock) {
            val words = full.split(" ")
            val sb = StringBuilder()
            words.forEachIndexed { i, word ->
                if (sb.isNotEmpty()) sb.append(' ')
                sb.append(word)
                publish(listOf(build(sb.toString())))
                // Every few words, a longer pause — the lumpy delivery the spread drain has to bridge.
                delay(if (i % 6 == 5) BURST_PAUSE_MS else WORD_DELAY_MS)
            }
            committed = committed + build(full)
        }

        try {
            streamWords(REASONING) { ContentBlock.Thinking(thinking = it) }
            delay(PHASE_PAUSE_MS)
            streamWords(NARRATION) { ContentBlock.Text(it) }
            delay(PHASE_PAUSE_MS)

            val tool = ContentBlock.ToolUse(id = "sim", name = "read_calendar", input = JsonObject(emptyMap()))
            committed = committed + tool
            publish(emptyList())
            delay(PHASE_PAUSE_MS)
            committed = committed + ContentBlock.ToolResult(
                tool_use_id = "sim",
                content = """{"events":[0,0,0,0,0,0]}""",
                is_error = false,
            )
            publish(emptyList())
            delay(PHASE_PAUSE_MS)

            streamWords(ANSWER) { ContentBlock.Text(it) }
            delay(PHASE_PAUSE_MS * 2)
        } finally {
            StreamingTurnStore.clear(SESSION_ID, turnIndex = 0)
        }
    }

    private const val REASONING =
        "Let me read the calendar for the next 24 hours and find the first event you must be ready for. " +
            "All-day markers like Office set the working location; a virtual stand-up is a real anchor with " +
            "no commute. I subtract the travel buffer and preparation time, then nudge into a light-sleep window."

    private const val NARRATION =
        "I can see tomorrow's picture clearly — a packed morning with an early stand-up and an office day."

    private const val ANSWER =
        "SUMMARY: Set a 6:40 alarm so you make your 9:00 stand-up\n\n" +
            "Set a **6:40** alarm so you make your 9:00 stand-up. Your first anchor is at the office, about a " +
            "25 min drive. I took the commute plus 45 min of `preparation_time` off the start, then landed on a " +
            "light-sleep moment just before."
}
