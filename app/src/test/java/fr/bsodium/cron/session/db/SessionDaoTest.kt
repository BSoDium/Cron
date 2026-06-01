package fr.bsodium.cron.session.db

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import fr.bsodium.cron.session.model.SessionStatus
import fr.bsodium.cron.testutil.Fixtures
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SessionDaoTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var db: CronDatabase
    private lateinit var dao: SessionDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            CronDatabase::class.java,
        ).setQueryExecutor(dispatcher.asExecutor())
            .setTransactionExecutor(dispatcher.asExecutor())
            .allowMainThreadQueries()
            .build()
        dao = db.sessionDao()
    }

    @After
    fun tearDown() = db.close()

    private fun entity(
        id: String,
        date: String,
        status: SessionStatus = SessionStatus.Monitoring,
        createdAtMs: Long = 0L,
    ) = Fixtures.session(
        id = id,
        date = LocalDate.parse(date),
        status = status,
        createdAt = Instant.fromEpochMilliseconds(createdAtMs),
        updatedAt = Instant.fromEpochMilliseconds(createdAtMs),
    ).toEntity()

    @Test
    fun insert_then_lookups_by_id_and_date() = runTest(dispatcher) {
        dao.insert(entity("s1", "2026-05-22"))
        assertEquals("s1", dao.findById("s1")?.id)
        assertEquals("s1", dao.findByDate("2026-05-22")?.id)
        assertNull(dao.findById("missing"))
        assertNull(dao.findByDate("2026-01-01"))
    }

    @Test
    fun insert_aborts_on_duplicate_date() = runTest(dispatcher) {
        dao.insert(entity("s1", "2026-05-22"))
        try {
            dao.insert(entity("s2", "2026-05-22"))
            fail("expected SQLiteConstraintException on the unique date index")
        } catch (_: SQLiteConstraintException) {
        }
    }

    @Test
    fun insertOrReplace_replaces_session_on_same_date() = runTest(dispatcher) {
        dao.insert(entity("s1", "2026-05-22"))
        dao.insertOrReplace(entity("s2", "2026-05-22"))
        assertEquals("s2", dao.findByDate("2026-05-22")?.id)
        assertNull(dao.findById("s1"))
    }

    @Test
    fun findCurrent_returns_newest_non_complete() = runTest(dispatcher) {
        dao.insert(entity("complete", "2026-05-20", SessionStatus.Complete, createdAtMs = 3_000))
        dao.insert(entity("old", "2026-05-21", SessionStatus.Monitoring, createdAtMs = 1_000))
        dao.insert(entity("current", "2026-05-22", SessionStatus.Monitoring, createdAtMs = 2_000))
        assertEquals("current", dao.findCurrent()?.id)
    }

    @Test
    fun observeLatest_emits_newest_on_insert() = runTest(dispatcher) {
        dao.insert(entity("a", "2026-05-21", createdAtMs = 1_000))
        dao.observeLatest().test {
            assertEquals("a", awaitItem()?.id)
            dao.insert(entity("b", "2026-05-22", createdAtMs = 2_000))
            assertEquals("b", awaitItem()?.id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun deleteOlderThan_removes_only_older_rows() = runTest(dispatcher) {
        dao.insert(entity("old", "2026-05-20", createdAtMs = 1_000))
        dao.insert(entity("new", "2026-05-22", createdAtMs = 5_000))
        assertEquals(1, dao.deleteOlderThan(3_000))
        assertNull(dao.findById("old"))
        assertEquals("new", dao.findById("new")?.id)
    }
}
