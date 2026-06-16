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
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.random.Random

/**
 * DEBUG-ONLY. Simulates a realistic multi-turn planning run through the [AnthropicMessages] seam
 * so the streaming UI can be tested without consuming API credits. Tool calls still execute
 * against real tools (calendar, location, etc.) — only the LLM responses are synthetic.
 *
 * Flow per run: read_calendar → estimate_commute → set_alarm → final answer.
 * Wired in via [fr.bsodium.cron.ai.AnthropicClientFactory] when [MockApiPrefs.isEnabled] is true.
 */
class FakeAnthropicClient : AnthropicMessages {

    private var call = 0

    override suspend fun send(request: MessagesRequest): MessagesResponse =
        throw UnsupportedOperationException("FakeAnthropicClient supports the streaming path only")

    override suspend fun stream(
        request: MessagesRequest,
        onPartial: suspend (List<ContentBlock>) -> Unit,
    ): MessagesResponse {
        val turn = call++
        Log.i(TAG, "stream turn=$turn model=${request.model}")
        return realisticRun(turn, request.model, onPartial)
    }

    private suspend fun realisticRun(
        turn: Int,
        model: String,
        onPartial: suspend (List<ContentBlock>) -> Unit,
    ): MessagesResponse = when (turn) {
        0 -> {
            if (Random.nextFloat() < ERROR_RATE) {
                injectStreamingError(onPartial)
            } else {
                val thinking = streamThinking(THINK_READ_CAL.random(), onPartial)
                val toolUse = ContentBlock.ToolUse(
                    id = SIM_CAL_ID,
                    name = "read_calendar",
                    input = buildJsonObject {
                        put("start_iso", SIM_START_ISO)
                        put("end_iso", SIM_END_ISO)
                    },
                )
                onPartial(listOf(thinking.signed(), toolUse))
                response(model, listOf(thinking.signed(), toolUse), stopReason = "tool_use")
            }
        }
        1 -> {
            val thinking = streamThinking(THINK_COMMUTE.random(), onPartial)
            val toolUse = ContentBlock.ToolUse(
                id = SIM_COMMUTE_ID,
                name = "estimate_commute",
                input = buildJsonObject {
                    put("origin_lat", SIM_LAT)
                    put("origin_lng", SIM_LNG)
                    put("destination", SIM_DESTINATION)
                    put("mode", "TRANSIT")
                    put("arrival_time_iso", SIM_ANCHOR_ISO)
                },
            )
            onPartial(listOf(thinking.signed(), toolUse))
            response(model, listOf(thinking.signed(), toolUse), stopReason = "tool_use")
        }
        2 -> {
            val thinking = streamThinking(THINK_ALARM.random(), onPartial)
            val toolUse = ContentBlock.ToolUse(
                id = SIM_ALARM_ID,
                name = "set_alarm",
                input = buildJsonObject {
                    put("time_iso", SIM_WAKE_ISO)
                    put("label", "Morning alarm")
                    put("reason", "45 min prep + transit to ${SIM_DESTINATION.substringBefore(",")}, arriving ${SIM_ANCHOR_ISO.take(16).replace("T", " ")} UTC")
                },
            )
            onPartial(listOf(thinking.signed(), toolUse))
            response(model, listOf(thinking.signed(), toolUse), stopReason = "tool_use")
        }
        else -> {
            val thinking = streamThinking(THINK_DONE.random(), onPartial)
            val text = streamText(ANSWERS.random(), listOf(thinking.signed()), onPartial)
            response(model, listOf(thinking.signed(), text))
        }
    }

    private suspend fun injectStreamingError(
        onPartial: suspend (List<ContentBlock>) -> Unit,
    ): MessagesResponse {
        val words = THINK_READ_CAL[0].split(" ").take(4)
        val sb = StringBuilder()
        for (word in words) {
            if (sb.isNotEmpty()) sb.append(' ')
            sb.append(word)
            onPartial(listOf(ContentBlock.Thinking(thinking = sb.toString())))
            delay(WORD_DELAY_MS)
        }
        throw AnthropicClient.AnthropicHttpException(
            code = 529,
            type = "overloaded_error",
            message = "[mock] API overloaded — exercising retry path",
        )
    }

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

    private fun ContentBlock.Thinking.signed() = copy(signature = SIM_SIGNATURE)

    private fun response(
        model: String,
        content: List<ContentBlock>,
        stopReason: String = "end_turn",
    ) = MessagesResponse(
        id = "sim-${call - 1}",
        model = model,
        role = "assistant",
        content = content,
        stop_reason = stopReason,
        usage = Usage(),
    )

    companion object {
        private const val TAG = "FakeAnthropicClient"
        private const val SIM_SIGNATURE = "sim-sig"
        private const val SIM_CAL_ID = "sim-cal-1"
        private const val SIM_COMMUTE_ID = "sim-commute-1"
        private const val SIM_ALARM_ID = "sim-alarm-1"
        private const val WORD_DELAY_MS = 20L
        private const val BURST_PAUSE_MS = 280L
        private const val ERROR_RATE = 0.10f

        // Fictional planning scenario: 8:45 in-person meeting, Paris.
        private const val SIM_START_ISO = "2025-12-17T00:00:00Z"
        private const val SIM_END_ISO = "2025-12-18T00:00:00Z"
        private const val SIM_ANCHOR_ISO = "2025-12-17T08:45:00Z"
        private const val SIM_WAKE_ISO = "2025-12-17T07:30:00Z"
        private const val SIM_LAT = "48.8566"
        private const val SIM_LNG = "2.3522"
        private const val SIM_DESTINATION = "Tour Montparnasse, Paris"

        private val THINK_READ_CAL = listOf(
            "Let me check the calendar for the next 24 hours to find the first hard anchor — any event you must be on time for. " +
                "Virtual stand-ups count even without a commute; I only subtract preparation time in that case. " +
                "All-day markers like 'Office' just set the working location, not a wake constraint.",
            "I'll scan tomorrow's schedule for the earliest commitment that cannot slide. " +
                "All-day events set context; time-bounded ones are the real constraints. " +
                "Once I have the anchor I'll work back through commute and prep time to find the ideal wake moment.",
            "Reading the calendar to locate the anchor event. I'm looking for the first hard start time — " +
                "a meeting, a class, or a commute-required appointment. " +
                "I'll ignore all-day events unless they're the only thing on the schedule.",
        )

        private val THINK_COMMUTE = listOf(
            "The calendar shows an 8:45 in-person meeting. I need the commute duration so I can work backwards " +
                "to the required departure time. Querying by transit from the current location, arriving a few minutes early.",
            "There's an 8:45 anchor at the office. Let me estimate the commute via transit — I want to arrive " +
                "with a couple of minutes to spare, so I'll target 8:43 as the arrival time.",
        )

        private val THINK_ALARM = listOf(
            "Transit comes in at about 22 minutes. I need to depart by 8:23, so waking at 7:40 gives me 45 minutes of " +
                "preparation. The nearest 90-minute light-sleep window lands at 7:30 — within the ±15 min snap tolerance. Using 7:30.",
            "Commute is roughly 26 minutes. Departure at 8:19, minus 45 minutes prep time, gives a 7:34 raw wake time. " +
                "The sleep cycle places a light-sleep moment at 7:30, well within tolerance. Setting the alarm there.",
        )

        private val THINK_DONE = listOf(
            "All set. The alarm is armed.",
            "Done.",
        )

        private val ANSWERS = listOf(
            "SUMMARY: Wake at 7:30 to make your 8:45 meeting\n\n" +
                "Set a **7:30** alarm for your 8:45 in-person at ${SIM_DESTINATION.substringBefore(",")}. " +
                "Transit is ~22 min; I added 45 min of preparation time and snapped to a light-sleep window.",
            "SUMMARY: Alarm set for 7:30 — 75 min before your 8:45 anchor\n\n" +
                "Your **7:30** alarm is set. You have an 8:45 meeting at ${SIM_DESTINATION.substringBefore(",")} (~26 min by transit). " +
                "I added 45 min prep time and landed on the nearest 90-minute light-sleep window.",
        )
    }
}
