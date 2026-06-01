package fr.bsodium.cron.session.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import fr.bsodium.cron.session.model.EventData
import fr.bsodium.cron.session.model.TriggerType
import fr.bsodium.cron.testutil.Fixtures
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.serialization.encodeToString
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
class EventDaoTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var db: CronDatabase
    private lateinit var dao: EventDao

    @Before
    fun setUp() = runTest(dispatcher) {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            CronDatabase::class.java,
        ).setQueryExecutor(dispatcher.asExecutor())
            .setTransactionExecutor(dispatcher.asExecutor())
            .allowMainThreadQueries()
            .build()
        dao = db.eventDao()
        db.sessionDao().insert(Fixtures.session(id = "s1", date = LocalDate.parse("2026-05-22")).toEntity())
    }

    @After
    fun tearDown() = db.close()

    private fun event(trigger: TriggerType, timestampMs: Long, sessionId: String = "s1") =
        SessionEventEntity(
            sessionId = sessionId,
            trigger = trigger.name,
            timestamp = timestampMs,
            dataJson = SessionJson.encodeToString<EventData>(EventData.Empty),
        )

    @Test
    fun insert_returns_id_and_findBySession_orders_by_insertion() = runTest(dispatcher) {
        val first = dao.insert(event(TriggerType.SleepOnset, 1_000))
        val second = dao.insert(event(TriggerType.HcStageUpdate, 2_000))
        assertTrue(second > first)
        assertEquals(listOf(first, second), dao.findBySession("s1").map { it.id })
    }

    @Test
    fun findLatestByTrigger_filters_and_takes_newest() = runTest(dispatcher) {
        dao.insert(event(TriggerType.HcStageUpdate, 1_000))
        dao.insert(event(TriggerType.SleepOnset, 1_500))
        dao.insert(event(TriggerType.HcStageUpdate, 2_000))
        assertEquals(2_000L, dao.findLatestByTrigger("s1", TriggerType.HcStageUpdate.name)?.timestamp)
        assertNull(dao.findLatestByTrigger("s1", TriggerType.AlarmDismissed.name))
    }

    @Test
    fun observeBySession_emits_on_insert() = runTest(dispatcher) {
        dao.observeBySession("s1").test {
            assertEquals(emptyList<SessionEventEntity>(), awaitItem())
            dao.insert(event(TriggerType.SleepOnset, 1_000))
            assertEquals(1, awaitItem().size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun deleting_session_cascades_to_events() = runTest(dispatcher) {
        dao.insert(event(TriggerType.SleepOnset, 1_000))
        db.sessionDao().deleteOlderThan(Long.MAX_VALUE)
        assertEquals(emptyList<SessionEventEntity>(), dao.findBySession("s1"))
    }
}
