package fr.bsodium.cron.ai.tools

import android.util.Log
import fr.bsodium.cron.ai.Tool
import fr.bsodium.cron.ai.ToolResult
import fr.bsodium.cron.ai.toolErrorResult
import fr.bsodium.cron.ai.toolSchema
import fr.bsodium.cron.ai.wire.ToolDefinition
import fr.bsodium.cron.alarm.AlarmScheduler
import fr.bsodium.cron.session.SessionRepository
import fr.bsodium.cron.session.db.SessionJson
import fr.bsodium.cron.session.model.ActionType
import fr.bsodium.cron.session.model.Instruction
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Terminal tool: arms the AI-controlled wake alarm and persists the instruction.
 *
 * This is the only path through which a new alarm gets scheduled during a
 * session. [AlarmScheduler] applies the client-side clamp to [hardLatest],
 * so the AI can never push the alarm past the safety floor.
 */
class SetAlarmTool(
    private val scheduler: AlarmScheduler,
    private val repository: SessionRepository,
    private val sessionId: String,
    private val sessionDate: LocalDate,
    private val timezone: TimeZone,
    private val hardLatest: LocalTime,
) : Tool {

    @Serializable
    private data class Output(
        val scheduled: Boolean,
        val alarm_time: String,
        val clamped_to_hard_latest: Boolean,
    )

    override val definition: ToolDefinition = ToolDefinition(
        name = NAME,
        description = "Schedule (or replace) the AI-controlled wake alarm. This is the terminal action — call it once you have decided on a wake time. The alarm is automatically clamped to never exceed the hard latest.",
        input_schema = toolSchema(
            "time_iso" to JsonObject(mapOf(
                "type" to JsonPrimitive("string"),
                "description" to JsonPrimitive("Desired alarm time as ISO-8601 instant, e.g. 2026-05-22T07:15:00Z"),
            )),
            "label" to JsonObject(mapOf(
                "type" to JsonPrimitive("string"),
                "description" to JsonPrimitive("Short alarm label shown to the user (e.g. 'Meeting at 9 AM')"),
            )),
            "reason" to JsonObject(mapOf(
                "type" to JsonPrimitive("string"),
                "description" to JsonPrimitive("One-sentence explanation of why this time was chosen"),
            )),
            required = listOf("time_iso", "label", "reason"),
        ),
    )

    override suspend fun execute(input: JsonElement): ToolResult {
        val obj = input.jsonObject
        val timeIso = obj["time_iso"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
            ?: return ToolResult("""{"error":"time_iso is required"}""", isError = true)
        val label = obj["label"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() } ?: "Cron Alarm"
        val reason = obj["reason"]?.jsonPrimitive?.content ?: ""

        val requested = runCatching { Instant.parse(timeIso) }.getOrNull()
            ?: return toolErrorResult("time_iso is not a valid ISO-8601 instant: $timeIso")

        val plan = scheduler.schedule(
            requested = requested,
            hardLatest = hardLatest,
            sessionDate = sessionDate,
            timezone = timezone,
            label = label,
            sessionId = sessionId,
        )

        val instruction = Instruction(
            action = ActionType.SetAlarm,
            alarmTime = plan.actualInstant.toLocalDateTime(timezone).time,
            reason = reason,
            issuedAt = Clock.System.now(),
        )
        repository.updateInstruction(sessionId, instruction)

        if (plan.clampedToHardLatest) {
            Log.w(TAG, "Alarm clamped to hard latest: requested=$requested actual=${plan.actualInstant}")
        }
        Log.i(TAG, "Alarm set: ${plan.actualInstant} for session $sessionId")

        return ToolResult(SessionJson.encodeToString(Output(
            scheduled = true,
            alarm_time = plan.actualInstant.toString(),
            clamped_to_hard_latest = plan.clampedToHardLatest,
        )))
    }

    companion object {
        const val NAME = "set_alarm"
        private const val TAG = "SetAlarmTool"
    }
}
