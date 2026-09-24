package fr.bsodium.cron.session.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import fr.bsodium.cron.testutil.Fixtures
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ObservationDaoTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var db: CronDatabase
    private lateinit var dao: ObservationDao

    @Before
    fun setUp() = runTest(dispatcher) {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            CronDatabase::class.java,
        ).setQueryExecutor(dispatcher.asExecutor())
            .setTransactionExecutor(dispatcher.asExecutor())
            .allowMainThreadQueries()
            .build()
        dao = db.observationDao()
        db.sessionDao().insert(Fixtures.session(id = "s1", date = LocalDate.parse("2026-05-22")).toEntity())
    }

    @After
    fun tearDown() = db.close()

    private fun observation(type: String, timestampMs: Long, sessionId: String = "s1") =
        ObservationEntity(sessionId = sessionId, type = type, timestamp = timestampMs, payloadJson = "{}")

    @Test
    fun insert_returns_id_and_findBySession_orders_by_insertion() = runTest(dispatcher) {
        val first = dao.insert(observation("screen_off", 1_000))
        val second = dao.insert(observation("screen_on", 2_000))
        assertEquals(listOf(first, second), dao.findBySession("s1").map { it.id })
    }

    @Test
    fun deleting_session_cascades_to_observations() = runTest(dispatcher) {
        dao.insert(observation("screen_off", 1_000))
        db.sessionDao().deleteOlderThan(Long.MAX_VALUE)
        assertEquals(emptyList<ObservationEntity>(), dao.findBySession("s1"))
    }
}
