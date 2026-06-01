package fr.bsodium.cron.ai.tools

import fr.bsodium.cron.ai.Tool
import fr.bsodium.cron.ai.ToolResult
import fr.bsodium.cron.ai.toolSchema
import fr.bsodium.cron.ai.wire.ToolDefinition
import fr.bsodium.cron.session.SessionRepository
import fr.bsodium.cron.session.model.ActionType
import fr.bsodium.cron.session.model.Instruction
import kotlinx.datetime.Clock
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Terminal tool: records a no-op decision and logs the AI's reasoning.
 *
 * Use when the current alarm is already optimal and no change is needed.
 * Calling this tool persists an updated instruction so the home screen
 * reflects the AI's latest considered state.
 */
class DoNothingTool(
    private val repository: SessionRepository,
    private val sessionId: String,
) : Tool {

    override val definition: ToolDefinition = ToolDefinition(
        name = NAME,
        description = "Record a no-op decision — use when the current alarm is already optimal and no change is needed. Always call this instead of stopping without a tool call, so the decision is logged.",
        input_schema = toolSchema(
            "reason" to JsonObject(mapOf(
                "type" to JsonPrimitive("string"),
                "description" to JsonPrimitive("One-sentence explanation of why no change is needed"),
            )),
            required = listOf("reason"),
        ),
    )

    override suspend fun execute(input: JsonElement): ToolResult {
        val reason = input.jsonObject["reason"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
            ?: return ToolResult("""{"error":"reason is required"}""", isError = true)

        // Keep the armed alarm: its time + window live only in currentInstruction, so carry them forward.
        val prior = repository.findById(sessionId)?.currentInstruction
        repository.updateInstruction(
            sessionId,
            Instruction(
                action = ActionType.DoNothing,
                alarmTime = prior?.alarmTime,
                wakeWindowStart = prior?.wakeWindowStart,
                wakeWindowEnd = prior?.wakeWindowEnd,
                reason = reason,
                issuedAt = Clock.System.now(),
            ),
        )
        return ToolResult("""{"logged":true}""")
    }

    companion object {
        const val NAME = "do_nothing"
    }
}
