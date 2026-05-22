package fr.bsodium.cron.ai.wire

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Anthropic Messages API request/response shapes.
 * https://docs.anthropic.com/en/api/messages
 *
 * Only the fields we use are modelled. Unknown fields are ignored by the
 * shared [fr.bsodium.cron.session.db.SessionJson] configuration.
 */

@Serializable
data class MessagesRequest(
    val model: String,
    val max_tokens: Int,
    val system: String? = null,
    val messages: List<MessageInput>,
    val tools: List<ToolDefinition>? = null,
    val tool_choice: ToolChoice? = null,
    val temperature: Double? = null,
)

@Serializable
data class MessageInput(
    val role: String, // "user" | "assistant"
    val content: List<ContentBlock>,
)

@Serializable
data class ToolDefinition(
    val name: String,
    val description: String,
    val input_schema: JsonObject,
)

@Serializable
sealed class ToolChoice {
    @Serializable @SerialName("auto") data object Auto : ToolChoice()
    @Serializable @SerialName("any") data object Any : ToolChoice()
    @Serializable @SerialName("tool")
    data class Tool(val name: String) : ToolChoice()
}

@Serializable
sealed class ContentBlock {

    @Serializable
    @SerialName("text")
    data class Text(val text: String) : ContentBlock()

    @Serializable
    @SerialName("tool_use")
    data class ToolUse(
        val id: String,
        val name: String,
        val input: JsonElement,
    ) : ContentBlock()

    @Serializable
    @SerialName("tool_result")
    data class ToolResult(
        val tool_use_id: String,
        val content: String,
        val is_error: Boolean? = null,
    ) : ContentBlock()
}

@Serializable
data class MessagesResponse(
    val id: String,
    val model: String,
    val role: String,
    val content: List<ContentBlock>,
    val stop_reason: String? = null,
    val stop_sequence: String? = null,
    val usage: Usage? = null,
)

@Serializable
data class Usage(
    val input_tokens: Int = 0,
    val output_tokens: Int = 0,
    val cache_creation_input_tokens: Int = 0,
    val cache_read_input_tokens: Int = 0,
)

@Serializable
data class ApiError(
    val type: String,
    val message: String,
)

@Serializable
data class ErrorEnvelope(
    val type: String = "error",
    val error: ApiError,
)
