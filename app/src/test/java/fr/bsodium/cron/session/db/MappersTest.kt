package fr.bsodium.cron.session.db

import fr.bsodium.cron.session.model.SleepStage
import fr.bsodium.cron.testutil.Fixtures
import fr.bsodium.cron.testutil.Fixtures.at
import org.junit.Assert.assertEquals
import org.junit.Test

class MappersTest {

    @Test
    fun session_survives_entity_round_trip() {
        val events = listOf(
            Fixtures.hcEvent(SleepStage.Light, at("2026-05-22T00:00:00Z"), at("2026-05-22T01:00:00Z")),
        )
        val session = Fixtures.session(events = events)

        assertEquals(session, session.toEntity().toModel(events))
    }

    @Test
    fun event_survives_entity_round_trip() {
        val event = Fixtures.hcEvent(SleepStage.Deep, at("2026-05-22T01:00:00Z"), at("2026-05-22T02:00:00Z"))

        assertEquals(event, event.toEntity(sessionId = "session-1").toModel())
    }
}
