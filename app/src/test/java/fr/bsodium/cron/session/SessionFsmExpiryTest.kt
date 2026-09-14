package fr.bsodium.cron.session

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.WorkManagerTestInitHelper
import fr.bsodium.cron.alarm.SessionExpiryScheduler
import fr.bsodium.cron.session.db.CronDatabase
import fr.bsodium.cron.session.db.toEntity
import fr.bsodium.cron.session.model.SessionStatus
import fr.bsodium.cron.testutil.Fixtures
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Regression coverage for #191/#192's active-window lifecycle: [SessionFsm.completeIfExpired]
 *  (the [fr.bsodium.cron.receiver.SessionExpiryReceiver] entry point) and the re-arm gap
 *  [SessionFsm.refreshPlanFromSettings] previously left in a mid-session hardLatest change. */
@RunWith(RobolectricTestRunner::class)
class SessionFsmExpiryTest {

    private val timezone = TimeZone.of("Europe/Paris")

    private lateinit var app: Application
    private lateinit var repository: SessionRepository
    private lateinit var fsm: SessionFsm

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(app)
        repository = SessionRepository(app)
        fsm = SessionFsm(app, repository)
        runBlocking { repository.clearAll() }
    }

    private fun insertSession(date: LocalDate, hardLatest: LocalTime, status: SessionStatus = SessionStatus.Monitoring) {
        runBlocking {
            CronDatabase.get(app).sessionDao().insert(
                Fixtures.session(
                    id = "s1",
                    date = date,
                    status = status,
                    plan = Fixtures.dayPlan(hardLatest = hardLatest),
                    timezone = timezone.id,
                ).toEntity(),
            )
        }
    }

    private fun tomorrow(): LocalDate = Clock.System.now().toLocalDateTime(timezone).date.plus(1, DateTimeUnit.DAY)
    private fun yesterday(): LocalDate = Clock.System.now().toLocalDateTime(timezone).date.minus(1, DateTimeUnit.DAY)

    @Test
    fun completeIfExpired_no_ops_on_an_already_complete_session() = runBlocking {
        insertSession(date = tomorrow(), hardLatest = LocalTime(10, 0), status = SessionStatus.Complete)
        assertFalse(fsm.completeIfExpired("s1"))
    }

    @Test
    fun completeIfExpired_no_ops_while_still_inside_the_window() = runBlocking {
        insertSession(date = tomorrow(), hardLatest = LocalTime(10, 0))
        assertFalse(fsm.completeIfExpired("s1"))
        assertEquals(SessionStatus.Monitoring, repository.findById("s1")?.status)
    }

    @Test
    fun completeIfExpired_completes_a_session_past_its_window() = runBlocking {
        // yesterday 00:01 + the 3h grace is comfortably in the past by the time any test runs today.
        insertSession(date = yesterday(), hardLatest = LocalTime(0, 1))
        assertTrue(fsm.completeIfExpired("s1"))
        assertEquals(SessionStatus.Complete, repository.findById("s1")?.status)
    }

    @Test
    fun refreshPlanFromSettings_rearms_session_expiry_when_hard_latest_changes() = runBlocking {
        val date = tomorrow()
        insertSession(date = date, hardLatest = LocalTime(9, 0)) // differs from the 10:00 settings default
        SessionExpiryScheduler(app).clear(date)
        assertFalse(SessionExpiryScheduler(app).isArmed(date))

        fsm.refreshPlanFromSettings("s1")

        assertTrue(SessionExpiryScheduler(app).isArmed(date))
    }
}
