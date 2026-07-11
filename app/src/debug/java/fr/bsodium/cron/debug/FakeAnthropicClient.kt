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
 *
 * A fresh instance is created per AI turn (see `AnthropicClientFactory.create`), so [scenario] is
 * picked once at construction — every turn (base plan or replan alike) gets its own random
 * destination/timing instead of the same hardcoded one, which is also what makes a replan's
 * resolved wake time actually differ from the previous turn's (needed for the timeline's PREV ›
 * NEW pair to ever render — it only shows when the two times differ).
 */
class FakeAnthropicClient : AnthropicMessages {

    private var call = 0
    private val scenario = SCENARIOS.random()

    override suspend fun send(request: MessagesRequest): MessagesResponse =
        throw UnsupportedOperationException("FakeAnthropicClient supports the streaming path only")

    override suspend fun stream(
        request: MessagesRequest,
        onPartial: suspend (List<ContentBlock>) -> Unit,
    ): MessagesResponse {
        val turn = call++
        Log.i(TAG, "stream turn=$turn model=${request.model} scenario=${scenario.destination}")
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
            val thinking = streamThinking(commuteThought(), onPartial)
            val toolUse = ContentBlock.ToolUse(
                id = SIM_COMMUTE_ID,
                name = "estimate_commute",
                input = buildJsonObject {
                    put("origin_lat", SIM_LAT)
                    put("origin_lng", SIM_LNG)
                    put("destination", scenario.destination)
                    put("mode", scenario.transitMode)
                    put("arrival_time_iso", scenario.anchorIso)
                },
            )
            onPartial(listOf(thinking.signed(), toolUse))
            response(model, listOf(thinking.signed(), toolUse), stopReason = "tool_use")
        }
        2 -> {
            val thinking = streamThinking(alarmThought(), onPartial)
            val toolUse = ContentBlock.ToolUse(
                id = SIM_ALARM_ID,
                name = "set_alarm",
                input = buildJsonObject {
                    put("time_iso", scenario.wakeIso)
                    put("label", "Morning alarm")
                    put(
                        "reason",
                        "${scenario.prepMinutes} min prep + transit to ${scenario.destination.substringBefore(",")}, " +
                            "arriving ${scenario.anchorIso.take(16).replace("T", " ")} UTC",
                    )
                },
            )
            onPartial(listOf(thinking.signed(), toolUse))
            response(model, listOf(thinking.signed(), toolUse), stopReason = "tool_use")
        }
        else -> { // turn is an unbounded call counter; every turn past the scripted 3 lands here
            val thinking = streamThinking(THINK_DONE.random(), onPartial)
            val text = streamText(finalAnswer(), listOf(thinking.signed()), onPartial)
            response(model, listOf(thinking.signed(), text))
        }
    }

    private fun commuteThought(): String {
        val place = scenario.destination.substringBefore(",")
        return COMMUTE_THOUGHT_TEMPLATES.random()
            .replace("%anchor%", scenario.anchorLabel)
            .replace("%place%", place)
    }

    private fun alarmThought(): String = ALARM_THOUGHT_TEMPLATES.random()
        .replace("%commute%", scenario.commuteMinutes.toString())
        .replace("%prep%", scenario.prepMinutes.toString())
        .replace("%rawWake%", scenario.rawWakeLabel)
        .replace("%wake%", scenario.wakeLabel)

    private fun finalAnswer(): String {
        val place = scenario.destination.substringBefore(",")
        return ANSWER_TEMPLATES.random()
            .replace("%wake%", scenario.wakeLabel)
            .replace("%anchor%", scenario.anchorLabel)
            .replace("%place%", place)
            .replace("%commute%", scenario.commuteMinutes.toString())
            .replace("%prep%", scenario.prepMinutes.toString())
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

    /** One self-contained planning scenario: a destination, an anchor event to be on time for, and
     *  the wake time it resolves to. Picked once per [FakeAnthropicClient] instance (i.e. once per
     *  AI turn) so a single run's calendar → commute → alarm steps stay narratively consistent,
     *  while different turns (a base plan vs. a later replan, each its own fresh instance) land on
     *  genuinely different destinations/times instead of the same hardcoded one every time. */
    private data class Scenario(
        val destination: String,
        val transitMode: String,
        val anchorIso: String,
        val anchorLabel: String,
        val commuteMinutes: Int,
        val prepMinutes: Int,
        val rawWakeLabel: String,
        val wakeLabel: String,
        val wakeIso: String,
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

        private const val SIM_START_ISO = "2025-12-17T00:00:00Z"
        private const val SIM_END_ISO = "2025-12-18T00:00:00Z"
        private const val SIM_LAT = "48.8566"
        private const val SIM_LNG = "2.3522"

        private val SCENARIOS = listOf(
            Scenario(
                destination = "Tour Montparnasse, Paris",
                transitMode = "TRANSIT",
                anchorIso = "2025-12-17T08:45:00Z",
                anchorLabel = "8:45 in-person meeting",
                commuteMinutes = 22,
                prepMinutes = 45,
                rawWakeLabel = "7:38",
                wakeLabel = "7:30",
                wakeIso = "2025-12-17T07:30:00Z",
            ),
            Scenario(
                destination = "Gare de Lyon, Paris",
                transitMode = "TRANSIT",
                anchorIso = "2025-12-17T07:15:00Z",
                anchorLabel = "7:15 train departure",
                commuteMinutes = 20,
                prepMinutes = 30,
                rawWakeLabel = "6:25",
                wakeLabel = "6:10",
                wakeIso = "2025-12-17T06:10:00Z",
            ),
            Scenario(
                destination = "La Défense, Paris",
                transitMode = "TRANSIT",
                anchorIso = "2025-12-17T09:00:00Z",
                anchorLabel = "9:00 all-hands",
                commuteMinutes = 32,
                prepMinutes = 40,
                rawWakeLabel = "7:48",
                wakeLabel = "7:35",
                wakeIso = "2025-12-17T07:35:00Z",
            ),
            Scenario(
                destination = "Le Marais, Paris",
                transitMode = "WALKING",
                anchorIso = "2025-12-17T10:30:00Z",
                anchorLabel = "10:30 client meeting",
                commuteMinutes = 25,
                prepMinutes = 35,
                rawWakeLabel = "9:30",
                wakeLabel = "9:20",
                wakeIso = "2025-12-17T09:20:00Z",
            ),
            Scenario(
                destination = "Charles de Gaulle Airport T2E, Roissy",
                transitMode = "TRANSIT",
                anchorIso = "2025-12-17T06:20:00Z",
                anchorLabel = "6:20 flight check-in",
                commuteMinutes = 48,
                prepMinutes = 60,
                rawWakeLabel = "4:32",
                wakeLabel = "4:20",
                wakeIso = "2025-12-17T04:20:00Z",
            ),
        )

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

        private val COMMUTE_THOUGHT_TEMPLATES = listOf(
            "The calendar shows a %anchor%. I need the commute duration so I can work backwards " +
                "to the required departure time. Querying by transit from the current location, arriving a few minutes early.",
            "There's a %anchor% at %place%. Let me estimate the commute — I want to arrive " +
                "with a couple of minutes to spare.",
        )

        private val ALARM_THOUGHT_TEMPLATES = listOf(
            "Transit comes in at about %commute% minutes. Waking at %rawWake% gives me %prep% minutes of " +
                "preparation. The nearest 90-minute light-sleep window lands at %wake% — within the ±15 min snap tolerance. Using %wake%.",
            "Commute is roughly %commute% minutes. Minus %prep% minutes of prep time gives a %rawWake% raw wake time. " +
                "The sleep cycle places a light-sleep moment at %wake%, well within tolerance. Setting the alarm there.",
        )

        private val THINK_DONE = listOf(
            "All set. The alarm is armed.",
            "Done.",
        )

        private val ANSWER_TEMPLATES = listOf(
            "SUMMARY: Wake at %wake% to make your %anchor%\n\n" +
                "Set a **%wake%** alarm for your %anchor% at %place%. " +
                "Transit is ~%commute% min; I added %prep% min of preparation time and snapped to a light-sleep window.",
            "SUMMARY: Alarm set for %wake% — ahead of your %anchor%\n\n" +
                "Your **%wake%** alarm is set. You have a %anchor% at %place% (~%commute% min by transit). " +
                "I added %prep% min prep time and landed on the nearest 90-minute light-sleep window.",
        )
    }
}
