package fr.bsodium.cron.ai

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import fr.bsodium.cron.ai.wire.ContentBlock
import fr.bsodium.cron.ai.wire.MessagesRequest
import fr.bsodium.cron.ai.wire.MessagesResponse
import fr.bsodium.cron.ai.wire.ToolDefinition
import fr.bsodium.cron.ai.wire.Usage
import fr.bsodium.cron.session.db.AiMessageDao
import fr.bsodium.cron.session.db.CronDatabase
import fr.bsodium.cron.session.db.SessionJson
import fr.bsodium.cron.session.db.toEntity
import fr.bsodium.cron.testutil.Fixtures
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class TurnRunnerStreamingTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var db: CronDatabase
    private lateinit var dao: AiMessageDao

    @Before
    fun setUp() = runTest(dispatcher) {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            CronDatabase::class.java,
        ).setQueryExecutor(dispatcher.asExecutor())
            .setTransactionExecutor(dispatcher.asExecutor())
            .allowMainThreadQueries()
            .build()
        dao = db.aiMessageDao()
        db.sessionDao().insert(Fixtures.session(id = "s1", date = LocalDate.parse("2026-05-22")).toEntity())
    }

    @After
    fun tearDown() {
        db.close()
        StreamingTurnStore.active.value?.let { StreamingTurnStore.clear(it.sessionId, it.turnIndex) }
    }

    @Test
    fun single_round_trip_streams_growing_partials_then_persists_complete() = runTest(dispatcher) {
        val fake = FakeAnthropic(
            listOf(
                Scripted(
                    partials = listOf(listOf(ContentBlock.Text("Hel")), listOf(ContentBlock.Text("Hello"))),
                    response = response(listOf(ContentBlock.Text("Hello")), stop = "end_turn"),
                ),
            ),
        )

        val outcome = runner(fake).run("s1", turnIndex = 0, initialUserMessage = "good evening")

        assertTrue(outcome is TurnRunner.Outcome.Completed)
        assertEquals(
            listOf("Hel", "Hello"),
            fake.published.map { (it.blocks.single() as ContentBlock.Text).text },
        )
        val rows = dao.findBySession("s1")
        assertEquals(listOf("user", "assistant"), rows.map { it.role })
        assertEquals(listOf(ContentBlock.Text("Hello")), decode(rows[1].contentJson))
        assertNull(StreamingTurnStore.active.value)
    }

    @Test
    fun tool_round_trip_accumulates_prior_blocks_and_round_trips_signature() = runTest(dispatcher) {
        val thinking = ContentBlock.Thinking(thinking = "let me check", signature = "sig-xyz")
        val toolUse = ContentBlock.ToolUse(id = "t1", name = "echo", input = JsonObject(emptyMap()))
        val fake = FakeAnthropic(
            listOf(
                Scripted(
                    partials = listOf(listOf(thinking)),
                    response = response(listOf(thinking, toolUse), stop = "tool_use"),
                ),
                Scripted(
                    partials = listOf(listOf(ContentBlock.Text("Do")), listOf(ContentBlock.Text("Done"))),
                    response = response(listOf(ContentBlock.Text("Done")), stop = "end_turn"),
                ),
            ),
        )

        val outcome = runner(fake).run("s1", turnIndex = 0, initialUserMessage = "good evening")

        assertTrue(outcome is TurnRunner.Outcome.Completed)
        // The 2nd round-trip's partial carries the prior round-trip's blocks ahead of new text.
        val lastPartial = fake.published.last().blocks
        assertTrue(lastPartial.any { it is ContentBlock.ToolUse })
        assertTrue(lastPartial.any { it is ContentBlock.ToolResult })
        assertEquals("Done", (lastPartial.last() as ContentBlock.Text).text)

        // DB holds complete messages only, in order.
        val rows = dao.findBySession("s1")
        assertEquals(listOf("user", "assistant", "user", "assistant"), rows.map { it.role })
        rows.forEach { decode(it.contentJson) } // decoding without throwing proves each row is complete JSON

        // The thinking signature is echoed back verbatim on the follow-up request.
        val echoed = fake.requests[1].messages
            .flatMap { it.content }
            .filterIsInstance<ContentBlock.Thinking>()
            .single()
        assertEquals("sig-xyz", echoed.signature)
        assertNull(StreamingTurnStore.active.value)
    }

    private fun runner(client: AnthropicMessages) = TurnRunner(
        client = client,
        aiMessageDao = dao,
        model = "claude-haiku-4-5",
        systemPrompt = "system",
        tools = ToolRegistry(listOf(EchoTool())),
        maxTokens = 1024,
    )

    private fun response(blocks: List<ContentBlock>, stop: String) = MessagesResponse(
        id = "msg",
        model = "claude-haiku-4-5",
        role = "assistant",
        content = blocks,
        stop_reason = stop,
        usage = Usage(output_tokens = 3),
    )

    private fun decode(json: String): List<ContentBlock> = SessionJson.decodeFromString(json)

    private data class Scripted(val partials: List<List<ContentBlock>>, val response: MessagesResponse)

    /** Plays a scripted stream per call; records the partial the runner published after each onPartial. */
    private class FakeAnthropic(private val script: List<Scripted>) : AnthropicMessages {
        val requests = mutableListOf<MessagesRequest>()
        val published = mutableListOf<StreamingTurn>()
        private var call = 0

        override suspend fun send(request: MessagesRequest): MessagesResponse =
            throw UnsupportedOperationException("streaming path only")

        override suspend fun stream(
            request: MessagesRequest,
            onPartial: suspend (List<ContentBlock>) -> Unit,
        ): MessagesResponse {
            requests += request
            val scripted = script[call++]
            scripted.partials.forEach { partial ->
                onPartial(partial)
                StreamingTurnStore.active.value?.let { published += it }
            }
            return scripted.response
        }
    }

    private class EchoTool : Tool {
        override val definition = ToolDefinition(name = "echo", description = "echoes", input_schema = JsonObject(emptyMap()))
        override suspend fun execute(input: JsonElement): ToolResult = ToolResult(payload = """{"ok":true}""")
    }
}
