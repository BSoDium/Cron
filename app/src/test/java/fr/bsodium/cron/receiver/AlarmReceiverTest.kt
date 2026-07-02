package fr.bsodium.cron.receiver

import android.app.Application
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.WorkManagerTestInitHelper
import fr.bsodium.cron.alarm.AlarmConstants
import fr.bsodium.cron.session.SessionRepository
import fr.bsodium.cron.session.db.CronDatabase
import fr.bsodium.cron.session.model.SessionStatus
import fr.bsodium.cron.session.model.TriggerType
import fr.bsodium.cron.settings.SettingsRepository
import fr.bsodium.cron.testutil.Fixtures
import fr.bsodium.cron.testutil.awaitCondition
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class AlarmReceiverTest {

    private lateinit var app: Application
    private lateinit var repository: SessionRepository

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(app)
        repository = SessionRepository(app)
        runBlocking {
            repository.clearAll()
            // Room's CronDatabase singleton and DataStore's backing file aren't reset between test
            // classes by Robolectric, so a disabled toggle can leak in from an unrelated test — reset it.
            SettingsRepository(app).setAutoAlarmsEnabled(true)
        }
    }

    @After
    fun tearDown() {
        runBlocking { repository.clearAll() }
    }

    private fun dispatch(action: String, extras: Intent.() -> Unit = {}) {
        val receiver = AlarmReceiver()
        ContextCompat.registerReceiver(app, receiver, IntentFilter(action), ContextCompat.RECEIVER_NOT_EXPORTED)
        app.sendBroadcast(Intent(action).apply(extras))
        shadowOf(Looper.getMainLooper()).idle()
        app.unregisterReceiver(receiver)
    }

    @Test
    fun alarm_fired_with_hard_latest_kind_appends_hard_latest_fired_event() = runBlocking {
        val session = repository.createSession(Fixtures.dayPlan(), Fixtures.DATE, "Europe/Paris")
        repository.updateStatus(session.id, SessionStatus.Monitoring)

        dispatch(AlarmReceiver.ACTION_ALARM_FIRED) {
            putExtra(AlarmConstants.EXTRA_KIND, AlarmConstants.KIND_HARD_LATEST)
            putExtra(AlarmConstants.EXTRA_SESSION_ID, session.id)
            putExtra(AlarmReceiver.EXTRA_LABEL, "Wake up")
        }

        awaitCondition {
            runBlocking { repository.findById(session.id)?.events?.any { it.trigger == TriggerType.HardLatestFired } == true }
        }
        // HardLatestFired is a no-op trigger — status must be unaffected.
        assertEquals(SessionStatus.Monitoring, repository.findById(session.id)?.status)
    }

    @Test
    fun dismiss_from_monitoring_rearms_to_awake_and_cancels_notification() = runBlocking {
        val session = repository.createSession(Fixtures.dayPlan(), Fixtures.DATE, "Europe/Paris")
        repository.updateStatus(session.id, SessionStatus.Monitoring)
        postNotification()

        dispatch(AlarmReceiver.ACTION_DISMISS)

        awaitCondition { runBlocking { repository.findById(session.id)?.status == SessionStatus.Awake } }
        assertTrue(activeNotificationIds().isEmpty())
    }

    @Test
    fun dismiss_with_no_active_session_does_not_crash() {
        postNotification()
        dispatch(AlarmReceiver.ACTION_DISMISS)
        shadowOf(Looper.getMainLooper()).idle()
        // No session to update; the notification is still cancelled unconditionally.
        assertTrue(activeNotificationIds().isEmpty())
    }

    @Test
    fun snooze_below_threshold_increments_count_and_stays_via_fsm() = runBlocking {
        val session = repository.createSession(Fixtures.dayPlan(), Fixtures.DATE, "Europe/Paris")
        repository.updateStatus(session.id, SessionStatus.Monitoring)

        dispatch(AlarmReceiver.ACTION_SNOOZE) {
            putExtra(AlarmReceiver.EXTRA_REQUEST_CODE, 42)
            putExtra(AlarmReceiver.EXTRA_LABEL, "Wake up")
        }

        awaitCondition { runBlocking { (repository.findById(session.id)?.events?.size ?: 0) > 0 } }
        assertEquals(1, repository.findById(session.id)?.events?.count { it.trigger == TriggerType.AlarmSnoozed })
    }

    @Test
    fun snooze_with_no_active_session_falls_back_to_simple_snooze_alarm() {
        dispatch(AlarmReceiver.ACTION_SNOOZE) {
            putExtra(AlarmReceiver.EXTRA_REQUEST_CODE, 7)
            putExtra(AlarmReceiver.EXTRA_LABEL, "Wake up")
        }

        awaitCondition { simpleSnoozePendingIntent(7) != null }
    }

    @Test
    fun snooze_with_corrupted_plan_json_falls_back_to_simple_snooze_alarm() = runBlocking {
        val session = repository.createSession(Fixtures.dayPlan(), Fixtures.DATE, "Europe/Paris")
        repository.updateStatus(session.id, SessionStatus.Monitoring)
        val dao = CronDatabase.get(app).sessionDao()
        val corrupted = requireNotNull(dao.findById(session.id)).copy(planJson = "{not valid json")
        dao.update(corrupted)

        dispatch(AlarmReceiver.ACTION_SNOOZE) {
            putExtra(AlarmReceiver.EXTRA_REQUEST_CODE, 13)
            putExtra(AlarmReceiver.EXTRA_LABEL, "Wake up")
        }

        awaitCondition { simpleSnoozePendingIntent(13) != null }
    }

    private fun postNotification() {
        // handleDismiss/handleSnooze cancel unconditionally; a real prior notification isn't required to
        // exercise that path, but posting one first is a closer approximation of the real ring→dismiss flow.
        val nm = app.getSystemService(NotificationManager::class.java)
        nm.notify(AlarmReceiver.NOTIFICATION_ID, android.app.Notification.Builder(app, AlarmReceiver.CHANNEL_ID).build())
    }

    private fun activeNotificationIds(): List<Int> =
        shadowOf(app.getSystemService(NotificationManager::class.java)).activeNotifications.map { it.id }

    private fun simpleSnoozePendingIntent(requestCode: Int): PendingIntent? =
        PendingIntent.getBroadcast(
            app,
            requestCode + 30000,
            Intent(app, AlarmReceiver::class.java).apply { action = AlarmReceiver.ACTION_ALARM_FIRED },
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
}
