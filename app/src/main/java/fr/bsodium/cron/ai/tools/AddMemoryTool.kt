package fr.bsodium.cron.ai.tools

import fr.bsodium.cron.ai.Tool
import fr.bsodium.cron.ai.ToolResult
import fr.bsodium.cron.ai.toolSchema
import fr.bsodium.cron.ai.wire.ToolDefinition
import fr.bsodium.cron.memory.MemoryRepository
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Adds a new durable memory entry. */
class AddMemoryTool(private val repository: MemoryRepository) : Tool {

    override val definition: ToolDefinition = ToolDefinition(
        name = NAME,
        description = "Record a new durable fact or preference about the user.",
        input_schema = toolSchema(
            "text" to JsonObject(mapOf(
                "type" to JsonPrimitive("string"),
                "description" to JsonPrimitive("The fact or preference to remember, as a short standalone sentence"),
            )),
            "category" to JsonObject(mapOf(
                "type" to JsonPrimitive("string"),
                "description" to JsonPrimitive("Optional short free-text label to group related entries"),
            )),
            required = listOf("text"),
        ),
    )

    override suspend fun execute(input: JsonElement): ToolResult {
        val text = input.jsonObject["text"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
            ?: return ToolResult("""{"error":"text is required"}""", isError = true)
        val category = input.jsonObject["category"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }

        val id = repository.add(text = text, category = category)
        return ToolResult("""{"id":$id,"logged":true}""")
    }

    companion object {
        const val NAME = "add_memory"
    }
}
