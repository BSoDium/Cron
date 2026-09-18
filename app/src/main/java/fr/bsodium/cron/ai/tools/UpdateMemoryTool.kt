package fr.bsodium.cron.ai.tools

import fr.bsodium.cron.ai.Tool
import fr.bsodium.cron.ai.ToolResult
import fr.bsodium.cron.ai.toolErrorResult
import fr.bsodium.cron.ai.toolSchema
import fr.bsodium.cron.ai.wire.ToolDefinition
import fr.bsodium.cron.memory.MemoryRepository
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Edits an existing durable memory entry's text and/or category. */
class UpdateMemoryTool(private val repository: MemoryRepository) : Tool {

    var wasCalled: Boolean = false
        private set

    override val definition: ToolDefinition = ToolDefinition(
        name = NAME,
        description = "Edit an existing memory entry's text and/or category, by its id.",
        input_schema = toolSchema(
            "id" to JsonObject(mapOf(
                "type" to JsonPrimitive("integer"),
                "description" to JsonPrimitive("The id of the memory entry to edit"),
            )),
            "text" to JsonObject(mapOf(
                "type" to JsonPrimitive("string"),
                "description" to JsonPrimitive("New text, replacing the entry's current text"),
            )),
            "category" to JsonObject(mapOf(
                "type" to JsonPrimitive("string"),
                "description" to JsonPrimitive("New category label, replacing the entry's current one"),
            )),
            required = listOf("id"),
        ),
    )

    override suspend fun execute(input: JsonElement): ToolResult {
        wasCalled = true
        val id = input.jsonObject["id"]?.jsonPrimitive?.content?.toLongOrNull()
            ?: return ToolResult("""{"error":"id is required"}""", isError = true)
        val text = input.jsonObject["text"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
        val category = input.jsonObject["category"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }

        val updated = repository.update(id = id, text = text, category = category)
        return if (updated) ToolResult("""{"updated":true}""") else toolErrorResult("no memory entry with id $id")
    }

    companion object {
        const val NAME = "update_memory"
    }
}
