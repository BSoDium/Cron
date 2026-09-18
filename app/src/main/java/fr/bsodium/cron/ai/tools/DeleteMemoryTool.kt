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

/** Removes a durable memory entry, by its id. */
class DeleteMemoryTool(private val repository: MemoryRepository) : Tool {

    var wasCalled: Boolean = false
        private set

    override val definition: ToolDefinition = ToolDefinition(
        name = NAME,
        description = "Delete a memory entry that's no longer true or relevant, by its id.",
        input_schema = toolSchema(
            "id" to JsonObject(mapOf(
                "type" to JsonPrimitive("integer"),
                "description" to JsonPrimitive("The id of the memory entry to delete"),
            )),
            required = listOf("id"),
        ),
    )

    override suspend fun execute(input: JsonElement): ToolResult {
        wasCalled = true
        val id = input.jsonObject["id"]?.jsonPrimitive?.content?.toLongOrNull()
            ?: return ToolResult("""{"error":"id is required"}""", isError = true)

        val deleted = repository.delete(id)
        return if (deleted) ToolResult("""{"deleted":true}""") else toolErrorResult("no memory entry with id $id")
    }

    companion object {
        const val NAME = "delete_memory"
    }
}
