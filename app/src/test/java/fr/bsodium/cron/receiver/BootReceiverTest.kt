package fr.bsodium.cron.receiver

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import fr.bsodium.cron.alarm.AlarmScheduler
import fr.bsodium.cron.alarm.HardLatestScheduler
import fr.bsodium.cron.service.SleepSessionService
import fr.bsodium.cron.session.db.CronDatabase
import fr.bsodium.cron.session.db.toEntity
import fr.bsodium.cron.session.model.ActionType
import fr.bsodium.cron.session.model.Instruction
import fr.bsodium.cron.session.model.SessionStatus
import fr.bsodium.cron.testutil.Fixtures
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class BootReceiverTest {

    private val timezone = TimeZone.of("Europe/Paris")

    /** Tomorrow in the session timezone, so hard-latest and alarm targets are always in the future. */
    private val sessionDate: LocalDate =
        Clock.System.now().toLocalDateTime(timezone).date.plus(1, DateTimeUnit.DAY)

    private lateinit var app: Application

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        // The production CronDatabase singleton is file-backed and persists across tests in the JVM;
        // wipe it so each test starts from a clean slate.
        runBlocking { CronDatabase.get(app).sessionDao().deleteOlderThan(Long.MAX_VALUE) }
    }

    private fun insertSession(
        status: SessionStatus = SessionStatus.Monitoring,
        instruction: Instruction = Fixtures.instruction(),
    ) = runBlocking {
        CronDatabase.get(app).sessionDao().insert(
            Fixtures.session(
                id = "boot-session",
                date = sessionDate,
                status = status,
                plan = Fixtures.dayPlan(hardLatest = LocalTime(10, 0)),
                currentInstruction = instruction,
                timezone = timezone.id,
            ).toEntity(),
        )
    }

    private fun recover() = runBlocking { BootReceiver().recoverActiveSession(app) }

    @Test
    fun active_session_with_planned_alarm_rearms_both_alarms_and_restarts_service() {
        insertSession(
            instruction = Fixtures.instruction(action = ActionType.SetAlarm, alarmTime = LocalTime(7, 0)),
        )

        recover()

        assertTrue(HardLatestScheduler(app).isArmed(sessionDate))
        assertTrue(AlarmScheduler(app).isArmed(sessionDate))
        val started = shadowOf(app).nextStartedService
        assertEquals(SleepSessionService::class.qualifiedName, started.component?.className)
        assertNull(started.action)
    }

    @Test
    fun active_session_without_planned_alarm_rearms_hard_latest_only_and_restarts_service() {
        insertSession(
            instruction = Fixtures.instruction(action = ActionType.DoNothing, alarmTime = null),
        )

        recover()

        assertTrue(HardLatestScheduler(app).isArmed(sessionDate))
        assertFalse(AlarmScheduler(app).isArmed(sessionDate))
        val started = shadowOf(app).nextStartedService
        assertEquals(SleepSessionService::class.qualifiedName, started.component?.className)
    }

    @Test
    fun complete_session_rearms_nothing_and_does_not_start_service() {
        insertSession(status = SessionStatus.Complete)

        recover()

        assertFalse(HardLatestScheduler(app).isArmed(sessionDate))
        assertFalse(AlarmScheduler(app).isArmed(sessionDate))
        assertNull(shadowOf(app).peekNextStartedService())
    }

    @Test
    fun no_session_rearms_nothing_and_does_not_start_service() {
        recover()

        assertFalse(HardLatestScheduler(app).isArmed(sessionDate))
        assertFalse(AlarmScheduler(app).isArmed(sessionDate))
        assertNull(shadowOf(app).peekNextStartedService())
    }
}
