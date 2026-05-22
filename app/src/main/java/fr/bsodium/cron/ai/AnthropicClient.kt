package fr.bsodium.cron.ai

import fr.bsodium.cron.ai.wire.ErrorEnvelope
import fr.bsodium.cron.ai.wire.MessagesRequest
import fr.bsodium.cron.ai.wire.MessagesResponse
import fr.bsodium.cron.session.db.SessionJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Thin client around the Anthropic Messages API.
 *
 * Networking concerns only — the [TurnRunner] is responsible for the tool-use
 * loop, persistence, and retries.
 */
class AnthropicClient(
    private val apiKeyProvider: () -> String?,
    private val client: OkHttpClient = defaultHttpClient(),
) {

    suspend fun send(request: MessagesRequest): MessagesResponse = withContext(Dispatchers.IO) {
        val apiKey = apiKeyProvider()
            ?: throw MissingApiKeyException()

        val body = SessionJson.encodeToString(request)
            .toRequestBody(JSON_MEDIA_TYPE)

        val httpRequest = Request.Builder()
            .url(MESSAGES_URL)
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .header("content-type", "application/json")
            .post(body)
            .build()

        client.newCall(httpRequest).execute().use { response ->
            val raw = response.body.string()
            if (!response.isSuccessful) {
                val parsed = runCatching { SessionJson.decodeFromString<ErrorEnvelope>(raw) }
                    .getOrNull()
                throw AnthropicHttpException(
                    code = response.code,
                    type = parsed?.error?.type,
                    message = parsed?.error?.message ?: raw.take(500),
                )
            }
            SessionJson.decodeFromString(raw)
        }
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
        private const val MESSAGES_URL = "https://api.anthropic.com/v1/messages"
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()

        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS) // Anthropic responses can be slow
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }
}
