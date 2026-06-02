package fr.bsodium.cron.ai

import fr.bsodium.cron.ai.wire.ContentBlock
import fr.bsodium.cron.ai.wire.MessageInput
import fr.bsodium.cron.ai.wire.MessagesRequest
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AnthropicClientStreamTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() = server.shutdown()

    private fun client() = AnthropicClient(
        apiKeyProvider = { "key" },
        client = AnthropicClient.defaultHttpClient(),
        messagesUrl = server.url("/v1/messages").toString(),
    )

    private val request = MessagesRequest(
        model = "claude-haiku-4-5",
        max_tokens = 64,
        messages = listOf(MessageInput(role = "user", content = listOf(ContentBlock.Text("hi")))),
    )

    @Test
    fun stream_emits_growing_partials_and_returns_full_message() = runTest {
        server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream").setBody(TEXT_STREAM))

        val partials = mutableListOf<List<ContentBlock>>()
        val response = client().stream(request) { partials.add(it) }

        assertEquals("end_turn", response.stop_reason)
        assertEquals(listOf(ContentBlock.Text("Hello world")), response.content)
        assertEquals(5, response.usage?.output_tokens)

        // Partials accumulate: text length is monotonic and the last one holds the full answer.
        val lengths = partials.map { (it.firstOrNull() as? ContentBlock.Text)?.text?.length ?: 0 }
        assertTrue(lengths.isNotEmpty())
        assertEquals(lengths.sorted(), lengths)
        assertEquals("Hello world", (partials.last().single() as ContentBlock.Text).text)

        assertTrue(server.takeRequest().body.readUtf8().contains("\"stream\":true"))
    }

    @Test
    fun http_429_maps_to_retryable_exception() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(429)
                .setBody("""{"type":"error","error":{"type":"rate_limit_error","message":"slow down"}}"""),
        )
        val error = runCatching { client().stream(request) {} }.exceptionOrNull()
        assertTrue(error is AnthropicClient.AnthropicHttpException)
        error as AnthropicClient.AnthropicHttpException
        assertEquals(429, error.code)
        assertEquals("rate_limit_error", error.type)
        assertTrue(error.isRetryable)
    }

    @Test
    fun mid_stream_error_event_throws_retryable() = runTest {
        server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream").setBody(ERROR_STREAM))
        val error = runCatching { client().stream(request) {} }.exceptionOrNull()
        assertTrue(error is AnthropicClient.AnthropicHttpException)
        assertEquals(529, (error as AnthropicClient.AnthropicHttpException).code)
        assertTrue(error.isRetryable)
    }

    private companion object {
        val TEXT_STREAM = """
            event: message_start
            data: {"type":"message_start","message":{"id":"msg_1","model":"claude-haiku-4-5","role":"assistant","usage":{"input_tokens":10,"output_tokens":1}}}

            event: content_block_start
            data: {"type":"content_block_start","index":0,"content_block":{"type":"text","text":""}}

            event: content_block_delta
            data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"Hello "}}

            event: ping
            data: {"type":"ping"}

            event: content_block_delta
            data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"world"}}

            event: content_block_stop
            data: {"type":"content_block_stop","index":0}

            event: message_delta
            data: {"type":"message_delta","delta":{"stop_reason":"end_turn"},"usage":{"output_tokens":5}}

            event: message_stop
            data: {"type":"message_stop"}

        """.trimIndent()

        val ERROR_STREAM = """
            event: message_start
            data: {"type":"message_start","message":{"id":"msg_2","model":"claude-haiku-4-5","role":"assistant"}}

            event: content_block_start
            data: {"type":"content_block_start","index":0,"content_block":{"type":"text","text":""}}

            event: error
            data: {"type":"error","error":{"type":"overloaded_error","message":"Overloaded"}}

        """.trimIndent()
    }
}
