package fr.bsodium.cron.session.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class MemoryDaoTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var db: CronDatabase
    private lateinit var dao: MemoryDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            CronDatabase::class.java,
        ).setQueryExecutor(dispatcher.asExecutor())
            .setTransactionExecutor(dispatcher.asExecutor())
            .allowMainThreadQueries()
            .build()
        dao = db.memoryDao()
    }

    @After
    fun tearDown() = db.close()

    private fun entry(text: String, category: String? = null, createdAt: Long = 0L) =
        MemoryEntity(text = text, category = category, createdAt = createdAt, updatedAt = createdAt)

    @Test
    fun findAll_orders_by_createdAt_ascending() = runTest(dispatcher) {
        dao.insert(entry("second", createdAt = 2L))
        dao.insert(entry("first", createdAt = 1L))
        assertEquals(listOf("first", "second"), dao.findAll().map { it.text })
    }

    @Test
    fun update_changes_the_row_in_place() = runTest(dispatcher) {
        val id = dao.insert(entry("original"))
        val updated = dao.findAll().first().copy(text = "revised", updatedAt = 5L)
        dao.update(updated)
        val result = dao.findAll().first { it.id == id }
        assertEquals("revised", result.text)
        assertEquals(5L, result.updatedAt)
    }

    @Test
    fun deleteById_removes_only_that_row() = runTest(dispatcher) {
        val keep = dao.insert(entry("keep"))
        val gone = dao.insert(entry("gone"))
        assertEquals(1, dao.deleteById(gone))
        assertEquals(listOf(keep), dao.findAll().map { it.id })
    }

    @Test
    fun deleteById_returns_zero_when_nothing_matched() = runTest(dispatcher) {
        assertEquals(0, dao.deleteById(999L))
    }

    @Test
    fun observeAll_emits_on_insert() = runTest(dispatcher) {
        dao.observeAll().test {
            assertEquals(emptyList<MemoryEntity>(), awaitItem())
            dao.insert(entry("new"))
            assertEquals(1, awaitItem().size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun deleteAll_clears_every_row() = runTest(dispatcher) {
        dao.insert(entry("a"))
        dao.insert(entry("b"))
        assertEquals(2, dao.deleteAll())
        assertTrue(dao.findAll().isEmpty())
    }
}
