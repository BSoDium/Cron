package fr.bsodium.cron.session.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.bsodium.cron.session.model.ActionType
import fr.bsodium.cron.session.model.DayPlan
import fr.bsodium.cron.session.model.EventData
import fr.bsodium.cron.session.model.Instruction
import fr.bsodium.cron.session.model.LocationPayload
import fr.bsodium.cron.session.model.LocationSource
import fr.bsodium.cron.session.model.SessionEvent
import fr.bsodium.cron.session.model.SessionStatus
import fr.bsodium.cron.session.model.SleepSession
import fr.bsodium.cron.session.model.TriggerType
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class SessionRoundTripTest {

    private lateinit var db: CronDatabase

    @Before
    fun setup() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, CronDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun session_and_events_roundtrip() = runBlocking {
        val now = Clock.System.now()
        val sessionId = UUID.randomUUID().toString()
        val date = LocalDate.parse("2026-05-21")

        val plan = DayPlan(
            hardLatest = LocalTime(10, 0),
            wakeWindowStart = LocalTime(7, 0),
            wakeWindowEnd = LocalTime(9, 30),
            firstEventId = "evt-1",
            firstEventTime = null,
            firstEventLocation = "Office",
            commuteBufferMinutes = 30,
            isFreeDayFallback = false,
            generatedAt = now,
        )
        val instruction = Instruction(
            action = ActionType.DoNothing,
            reason = "initial",
            issuedAt = now,
        )
        val session = SleepSession(
            id = sessionId,
            date = date,
            status = SessionStatus.Planning,
            plan = plan,
            currentInstruction = instruction,
            events = emptyList(),
            lastAiCallAt = null,
            snoozeCount = 0,
            timezone = "Europe/Paris",
            createdAt = now,
            updatedAt = now,
        )

        db.sessionDao().insert(session.toEntity())

        val event = SessionEvent(
            trigger = TriggerType.EveningPlan,
            timestamp = now,
            data = EventData.EveningPlan(
                timezone = "Europe/Paris",
                location = LocationPayload(
                    lat = 48.85,
                    lng = 2.35,
                    accuracyMeters = 12f,
                    source = LocationSource.Gps,
                    capturedAt = now,
                ),
            ),
        )
        db.eventDao().insert(event.toEntity(sessionId))

        val loadedEntity = db.sessionDao().findById(sessionId)
        assertNotNull(loadedEntity)
        val loadedEvents = db.eventDao().findBySession(sessionId).map { it.toModel() }
        val loaded = loadedEntity!!.toModel(loadedEvents)

        assertEquals(sessionId, loaded.id)
        assertEquals(date, loaded.date)
        assertEquals(SessionStatus.Planning, loaded.status)
        assertEquals(LocalTime(10, 0), loaded.plan.hardLatest)
        assertEquals(ActionType.DoNothing, loaded.currentInstruction.action)
        assertEquals(1, loaded.events.size)
        assertEquals(TriggerType.EveningPlan, loaded.events.first().trigger)
        val data = loaded.events.first().data as EventData.EveningPlan
        assertEquals("Europe/Paris", data.timezone)
        assertEquals(LocationSource.Gps, data.location.source)
    }
}
