package fr.bsodium.cron.ai.wire

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Server-sent events emitted by the Anthropic Messages API when `stream = true`.
 * https://docs.anthropic.com/en/api/messages-streaming
 *
 * Modelled as its own sealed root on kotlinx's default `"type"` discriminator — wire-faithful
 * and independent of [ContentBlock], which shares the same discriminator without colliding
 * (each sealed root resolves its discriminator from its own declaration). Field names mirror the
 * snake_case wire shape, matching [MessagesRequest]/[MessagesResponse]. Unknown keys (and event
 * types we don't model) are tolerated by [fr.bsodium.cron.session.db.SessionJson].
 */
@Serializable
sealed class StreamEvent {

    @Serializable
    @SerialName("message_start")
    data class MessageStart(val message: MessageStartInfo) : StreamEvent()

    @Serializable
    @SerialName("content_block_start")
    data class ContentBlockStart(val index: Int, val content_block: ContentBlock) : StreamEvent()

    @Serializable
    @SerialName("content_block_delta")
    data class ContentBlockDelta(val index: Int, val delta: Delta) : StreamEvent()

    @Serializable
    @SerialName("content_block_stop")
    data class ContentBlockStop(val index: Int) : StreamEvent()

    @Serializable
    @SerialName("message_delta")
    data class MessageDelta(val delta: MessageDeltaInfo, val usage: Usage? = null) : StreamEvent()

    @Serializable
    @SerialName("message_stop")
    data object MessageStop : StreamEvent()

    @Serializable
    @SerialName("ping")
    data object Ping : StreamEvent()

    @Serializable
    @SerialName("error")
    data class Error(val error: ApiError) : StreamEvent()
}

@Serializable
data class MessageStartInfo(
    val id: String,
    val model: String,
    val role: String,
    val usage: Usage? = null,
)

@Serializable
data class MessageDeltaInfo(
    val stop_reason: String? = null,
    val stop_sequence: String? = null,
)

/** Incremental update to the content block at a given index. */
@Serializable
sealed class Delta {

    @Serializable
    @SerialName("text_delta")
    data class TextDelta(val text: String) : Delta()

    @Serializable
    @SerialName("thinking_delta")
    data class ThinkingDelta(val thinking: String) : Delta()

    @Serializable
    @SerialName("input_json_delta")
    data class InputJsonDelta(val partial_json: String) : Delta()

    @Serializable
    @SerialName("signature_delta")
    data class SignatureDelta(val signature: String) : Delta()
}
