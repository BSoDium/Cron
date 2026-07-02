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
import fr.bsodium.cron.session.db.AiMessageEntity
import fr.bsodium.cron.session.db.CronDatabase
import fr.bsodium.cron.session.db.SessionJson
import fr.bsodium.cron.session.db.toEntity
import fr.bsodium.cron.testutil.Fixtures
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class TurnRunnerResumeTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var db: CronDatabase
    private lateinit var dao: AiMessageDao
    private lateinit var echoTool: CountingEchoTool

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
        echoTool = CountingEchoTool()
        db.sessionDao().insert(Fixtures.session(id = "s1").toEntity())
    }

    @After
    fun tearDown() {
        db.close()
        StreamingTurnStore.active.value?.let { StreamingTurnStore.clear(it.sessionId, it.turnIndex) }
    }

    @Test
    fun resume_with_unanswered_tool_use_runs_the_tool_before_the_first_api_call() = runTest(dispatcher) {
        persist(role = "user", blocks = listOf(ContentBlock.Text("prompt")))
        persist(role = "assistant", blocks = listOf(toolUse("t1")))
        val fake = FakeAnthropic(listOf(response(listOf(ContentBlock.Text("Done")), stop = "end_turn")))

        val outcome = runner(fake).run("s1", turnIndex = 0, initialUserMessage = "ignored on resume")

        assertTrue(outcome is TurnRunner.Outcome.Completed)
        assertEquals(1, echoTool.executions)
        assertEquals(1, fake.requests.size)
        // The single request carries the resumed history plus the freshly-run tool results.
        val roles = fake.requests.single().messages.map { it.role }
        assertEquals(listOf("user", "assistant", "user"), roles)
        val lastContent = fake.requests.single().messages.last().content.single()
        assertEquals("t1", (lastContent as ContentBlock.ToolResult).tool_use_id)
        assertEquals(
            listOf("user", "assistant", "user", "assistant"),
            dao.findBySession("s1").map { it.role },
        )
    }

    @Test
    fun resume_from_seed_only_does_not_duplicate_the_seed_row() = runTest(dispatcher) {
        persist(role = "user", blocks = listOf(ContentBlock.Text("prompt")))
        val fake = FakeAnthropic(listOf(response(listOf(ContentBlock.Text("Done")), stop = "end_turn")))

        val outcome = runner(fake).run("s1", turnIndex = 0, initialUserMessage = "ignored on resume")

        assertTrue(outcome is TurnRunner.Outcome.Completed)
        assertEquals(0, echoTool.executions)
        assertEquals(listOf("user", "assistant"), dao.findBySession("s1").map { it.role })
        assertEquals("prompt", (fake.requests.single().messages.first().content.single() as ContentBlock.Text).text)
    }

    private suspend fun persist(role: String, blocks: List<ContentBlock>) {
        dao.insert(
            AiMessageEntity(
                sessionId = "s1",
                turnIndex = 0,
                role = role,
                contentJson = SessionJson.encodeToString(blocks),
                createdAt = 0L,
            )
        )
    }

    private fun runner(client: AnthropicMessages) = TurnRunner(
        client = client,
        aiMessageDao = dao,
        model = "claude-haiku-4-5",
        systemPrompt = "system",
        tools = ToolRegistry(listOf(echoTool)),
        maxTokens = 1024,
    )

    private fun toolUse(id: String) = ContentBlock.ToolUse(id = id, name = "echo", input = JsonObject(emptyMap()))

    private fun response(blocks: List<ContentBlock>, stop: String) = MessagesResponse(
        id = "msg",
        model = "claude-haiku-4-5",
        role = "assistant",
        content = blocks,
        stop_reason = stop,
        usage = Usage(output_tokens = 3),
    )

    private class FakeAnthropic(private val script: List<MessagesResponse>) : AnthropicMessages {
        val requests = mutableListOf<MessagesRequest>()

        override suspend fun send(request: MessagesRequest): MessagesResponse =
            throw UnsupportedOperationException("streaming path only")

        override suspend fun stream(
            request: MessagesRequest,
            onPartial: suspend (List<ContentBlock>) -> Unit,
        ): MessagesResponse {
            requests += request
            return script[requests.size - 1]
        }
    }

    private class CountingEchoTool : Tool {
        var executions = 0
        override val definition = ToolDefinition(name = "echo", description = "echoes", input_schema = JsonObject(emptyMap()))
        override suspend fun execute(input: JsonElement): ToolResult {
            executions++
            return ToolResult(payload = """{"ok":true}""")
        }
    }
}
