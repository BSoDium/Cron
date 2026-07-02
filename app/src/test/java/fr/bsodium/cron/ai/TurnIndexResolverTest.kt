package fr.bsodium.cron.ai

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import fr.bsodium.cron.ai.wire.ContentBlock
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
import kotlinx.serialization.json.JsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class TurnIndexResolverTest {

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
        db.sessionDao().insert(Fixtures.session(id = "s1").toEntity())
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun no_rows_starts_at_zero() = runTest(dispatcher) {
        assertEquals(0, TurnIndexResolver.resolve(dao, "s1", isRetry = false))
        assertEquals(0, TurnIndexResolver.resolve(dao, "s1", isRetry = true))
    }

    @Test
    fun settled_turn_always_starts_the_next_one() = runTest(dispatcher) {
        insert(turn = 3, role = "user", blocks = listOf(ContentBlock.Text("prompt")))
        insert(turn = 3, role = "assistant", blocks = listOf(ContentBlock.Text("done")))

        assertEquals(4, TurnIndexResolver.resolve(dao, "s1", isRetry = false))
        assertEquals(4, TurnIndexResolver.resolve(dao, "s1", isRetry = true))
    }

    @Test
    fun retry_resumes_a_turn_holding_only_the_seed() = runTest(dispatcher) {
        insert(turn = 2, role = "user", blocks = listOf(ContentBlock.Text("prompt")))

        assertEquals(2, TurnIndexResolver.resolve(dao, "s1", isRetry = true))
    }

    @Test
    fun retry_resumes_a_turn_with_unanswered_tool_use() = runTest(dispatcher) {
        insert(turn = 5, role = "user", blocks = listOf(ContentBlock.Text("prompt")))
        insert(turn = 5, role = "assistant", blocks = listOf(toolUse("t1")))

        assertEquals(5, TurnIndexResolver.resolve(dao, "s1", isRetry = true))
    }

    @Test
    fun first_attempt_never_resumes_a_partial_turn() = runTest(dispatcher) {
        insert(turn = 5, role = "user", blocks = listOf(ContentBlock.Text("prompt")))
        insert(turn = 5, role = "assistant", blocks = listOf(toolUse("t1")))

        assertEquals(6, TurnIndexResolver.resolve(dao, "s1", isRetry = false))
    }

    @Test
    fun undecodable_last_row_starts_fresh() = runTest(dispatcher) {
        dao.insert(
            AiMessageEntity(sessionId = "s1", turnIndex = 1, role = "assistant", contentJson = "not json", createdAt = 0L)
        )

        assertEquals(2, TurnIndexResolver.resolve(dao, "s1", isRetry = true))
    }

    private suspend fun insert(turn: Int, role: String, blocks: List<ContentBlock>) {
        dao.insert(
            AiMessageEntity(
                sessionId = "s1",
                turnIndex = turn,
                role = role,
                contentJson = SessionJson.encodeToString(blocks),
                createdAt = 0L,
            )
        )
    }

    private fun toolUse(id: String) = ContentBlock.ToolUse(id = id, name = "echo", input = JsonObject(emptyMap()))
}
