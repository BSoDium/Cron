package fr.bsodium.cron.ai

import fr.bsodium.cron.ai.wire.ToolDefinition
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * A single tool the model can invoke. Each tool declares its JSON Schema
 * (`definition.input_schema`) and a coroutine [execute] that produces a
 * string payload sent back to the model as a tool_result content block.
 */
interface Tool {
    val definition: ToolDefinition

    val name: String get() = definition.name

    /**
     * Execute the tool given the model-provided [input]. Implementations
     * must NOT throw for "expected" failure modes (permission denied, no
     * data, etc.) — instead, return a structured JSON string the model can
     * reason about. Throwing is reserved for unrecoverable bugs.
     */
    suspend fun execute(input: JsonElement): ToolResult
}

/**
 * Wrapper for the JSON payload sent back to the model. [isError] becomes
 * the `is_error` flag on the tool_result block; set it true for fatal
 * problems the model should not retry.
 */
data class ToolResult(
    val payload: String,
    val isError: Boolean = false,
)

/** Convenience builder used by tools to encode their input_schema. */
fun toolSchema(vararg properties: Pair<String, JsonElement>, required: List<String> = emptyList()): JsonObject {
    val schema = mutableMapOf<String, JsonElement>(
        "type" to kotlinx.serialization.json.JsonPrimitive("object"),
        "properties" to JsonObject(properties.toMap()),
    )
    if (required.isNotEmpty()) {
        schema["required"] = kotlinx.serialization.json.JsonArray(
            required.map { kotlinx.serialization.json.JsonPrimitive(it) },
        )
    }
    return JsonObject(schema)
}
