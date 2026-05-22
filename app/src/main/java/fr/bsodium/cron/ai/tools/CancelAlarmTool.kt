package fr.bsodium.cron.ai.tools

import fr.bsodium.cron.ai.Tool
import fr.bsodium.cron.ai.ToolResult
import fr.bsodium.cron.ai.toolSchema
import fr.bsodium.cron.ai.wire.ToolDefinition
import fr.bsodium.cron.alarm.AlarmScheduler
import fr.bsodium.cron.session.SessionRepository
import fr.bsodium.cron.session.model.ActionType
import fr.bsodium.cron.session.model.Instruction
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Terminal tool: cancels the AI-controlled alarm for this session.
 *
 * The hard-latest safety alarm is NOT cancelled — it remains armed as the
 * absolute floor. Use when the user is already awake and the AI alarm
 * would be redundant (e.g., OutOfBedConfirmed with 10+ minutes elapsed).
 */
class CancelAlarmTool(
    private val scheduler: AlarmScheduler,
    private val repository: SessionRepository,
    private val sessionId: String,
    private val sessionDate: LocalDate,
) : Tool {

    override val definition: ToolDefinition = ToolDefinition(
        name = NAME,
        description = "Cancel the AI-controlled wake alarm. The hard-latest safety alarm stays armed. Use when the user is already awake and the alarm is no longer needed.",
        input_schema = toolSchema(
            "reason" to JsonObject(mapOf(
                "type" to JsonPrimitive("string"),
                "description" to JsonPrimitive("One-sentence explanation of why the alarm is being cancelled"),
            )),
            required = listOf("reason"),
        ),
    )

    override suspend fun execute(input: JsonElement): ToolResult {
        val reason = input.jsonObject["reason"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
            ?: return ToolResult("""{"error":"reason is required"}""", isError = true)

        scheduler.cancel(sessionDate)
        repository.updateInstruction(
            sessionId,
            Instruction(
                action = ActionType.CancelAlarm,
                reason = reason,
                issuedAt = Clock.System.now(),
            ),
        )
        return ToolResult("""{"cancelled":true}""")
    }

    companion object {
        const val NAME = "cancel_alarm"
    }
}
