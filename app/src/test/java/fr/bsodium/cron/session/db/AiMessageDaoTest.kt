package fr.bsodium.cron.session.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import fr.bsodium.cron.testutil.Fixtures
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AiMessageDaoTest {

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
    fun tearDown() = db.close()

    private fun message(turn: Int, role: String = "assistant", sessionId: String = "s1") =
        AiMessageEntity(sessionId = sessionId, turnIndex = turn, role = role, contentJson = "[]", createdAt = 0L)

    @Test
    fun findByTurn_filters_to_one_turn_in_insertion_order() = runTest(dispatcher) {
        val user = dao.insert(message(turn = 0, role = "user"))
        val assistant = dao.insert(message(turn = 0, role = "assistant"))
        dao.insert(message(turn = 1, role = "user"))

        assertEquals(listOf(user, assistant), dao.findByTurn("s1", 0).map { it.id })
    }

    @Test
    fun maxTurnIndex_is_null_when_empty_then_tracks_highest() = runTest(dispatcher) {
        assertNull(dao.maxTurnIndex("s1"))
        dao.insert(message(turn = 0))
        dao.insert(message(turn = 2))
        dao.insert(message(turn = 1))
        assertEquals(2, dao.maxTurnIndex("s1"))
    }

    @Test
    fun deleteByTurn_removes_only_that_turn() = runTest(dispatcher) {
        dao.insert(message(turn = 0))
        dao.insert(message(turn = 1))
        dao.deleteByTurn("s1", 0)
        assertEquals(emptyList<AiMessageEntity>(), dao.findByTurn("s1", 0))
        assertEquals(1, dao.findByTurn("s1", 1).size)
    }

    @Test
    fun observeBySession_emits_on_insert() = runTest(dispatcher) {
        dao.observeBySession("s1").test {
            assertEquals(emptyList<AiMessageEntity>(), awaitItem())
            dao.insert(message(turn = 0))
            assertEquals(1, awaitItem().size)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
