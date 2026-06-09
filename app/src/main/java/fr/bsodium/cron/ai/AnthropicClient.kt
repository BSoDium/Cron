package fr.bsodium.cron.ai

import android.util.Log
import fr.bsodium.cron.ai.wire.ContentBlock
import fr.bsodium.cron.ai.wire.ErrorEnvelope
import fr.bsodium.cron.ai.wire.MessagesRequest
import fr.bsodium.cron.ai.wire.MessagesResponse
import fr.bsodium.cron.ai.wire.StreamEvent
import fr.bsodium.cron.session.db.SessionJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Network seam for the Anthropic Messages API: a blocking [send] and an SSE [stream]. The
 * [TurnRunner] depends on this interface so tests can swap in a fake without a live server.
 */
interface AnthropicMessages {
    suspend fun send(request: MessagesRequest): MessagesResponse

    /**
     * Streams [request] (forcing `stream = true`), invoking [onPartial] with the blocks decoded
     * so far as deltas arrive, and returns the fully reassembled message — identical in shape to
     * what [send] would return for the same request.
     */
    suspend fun stream(
        request: MessagesRequest,
        onPartial: suspend (List<ContentBlock>) -> Unit,
    ): MessagesResponse
}

/**
 * Thin client around the Anthropic Messages API.
 *
 * Networking concerns only — the [TurnRunner] is responsible for the tool-use
 * loop, persistence, and retries.
 */
class AnthropicClient(
    private val apiKeyProvider: () -> String?,
    private val client: OkHttpClient = defaultHttpClient(),
    private val messagesUrl: String = MESSAGES_URL,
) : AnthropicMessages {

    override suspend fun send(request: MessagesRequest): MessagesResponse = withContext(Dispatchers.IO) {
        client.newCall(buildRequest(request)).execute().use { response ->
            val raw = response.body.string()
            if (!response.isSuccessful) throw httpException(response.code, raw)
            SessionJson.decodeFromString(raw)
        }
    }

    override suspend fun stream(
        request: MessagesRequest,
        onPartial: suspend (List<ContentBlock>) -> Unit,
    ): MessagesResponse = withContext(Dispatchers.IO) {
        client.newCall(buildRequest(request.copy(stream = true))).execute().use { response ->
            if (!response.isSuccessful) throw httpException(response.code, response.body.string())

            val accumulator = StreamAccumulator()
            val source = response.body.source()
            while (true) {
                ensureActive() // let a cancelled turn (user cancel / WorkManager stop) break the read
                val line = source.readUtf8Line() ?: break
                if (!line.startsWith(DATA_PREFIX)) continue // event:/id:/comment/blank lines
                val payload = line.removePrefix(DATA_PREFIX).trim()
                val event = runCatching { SessionJson.decodeFromString<StreamEvent>(payload) }
                    .onFailure { Log.w(TAG, "drop unparseable stream event: ${payload.take(120)}", it) }
                    .getOrNull() ?: continue
                val terminal = accumulator.apply(event)
                if (event is StreamEvent.ContentBlockStart || event is StreamEvent.ContentBlockDelta) {
                    onPartial(accumulator.snapshotBlocks())
                }
                if (terminal) break
            }
            accumulator.toResponse(fallbackModel = request.model)
        }
    }

    private fun buildRequest(request: MessagesRequest): Request {
        val apiKey = apiKeyProvider() ?: throw MissingApiKeyException()
        val body = SessionJson.encodeToString(request).toRequestBody(JSON_MEDIA_TYPE)
        return Request.Builder()
            .url(messagesUrl)
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            // Lets the model think again after each tool result (e.g. reason over the actual calendar
            // events before choosing the anchor). No-op on requests without thinking enabled (replans).
            .header("anthropic-beta", "interleaved-thinking-2025-05-14")
            .header("content-type", "application/json")
            .post(body)
            .build()
    }

    private fun httpException(code: Int, raw: String): AnthropicHttpException {
        val parsed = runCatching { SessionJson.decodeFromString<ErrorEnvelope>(raw) }
            .onFailure { Log.w(TAG, "decode error envelope failed", it) }
            .getOrNull()
        return AnthropicHttpException(
            code = code,
            type = parsed?.error?.type,
            message = parsed?.error?.message ?: raw.take(500),
        )
    }

    class MissingApiKeyException : IllegalStateException("Anthropic API key is not configured")

    class AnthropicHttpException(
        val code: Int,
        val type: String?,
        message: String,
    ) : RuntimeException("Anthropic API $code ${type ?: ""}: $message") {
        val isRetryable: Boolean get() = code == 429 || code in 500..599
    }

    companion object {
        private const val TAG = "AnthropicClient"
        private const val DATA_PREFIX = "data:"
        private const val MESSAGES_URL = "https://api.anthropic.com/v1/messages"
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()

        /**
         * The 120s read timeout is per-read; on a stream it bounds the gap *between* events, not the
         * whole response. Anthropic's `ping` events keep the socket warm during long thinking pauses.
         */
        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }
}
