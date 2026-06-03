package fr.bsodium.cron.ai

import app.cash.turbine.test
import fr.bsodium.cron.ai.wire.ContentBlock
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class StreamingTurnStoreTest {

    /** The store is a process-wide singleton — reset it around each test. */
    @Before
    @After
    fun reset() {
        StreamingTurnStore.active.value?.let { StreamingTurnStore.clear(it.sessionId, it.turnIndex) }
    }

    private fun turn(sessionId: String, index: Int, text: String) =
        StreamingTurn(sessionId, index, listOf(ContentBlock.Text(text)), startedAtMs = 0L)

    @Test
    fun active_emits_latest_update() = runTest {
        StreamingTurnStore.active.test {
            assertNull(awaitItem())
            StreamingTurnStore.update(turn("s1", 0, "hi"))
            assertEquals("hi", (awaitItem()?.blocks?.single() as ContentBlock.Text).text)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun clear_nulls_the_matching_turn() {
        StreamingTurnStore.update(turn("s1", 0, "hi"))
        StreamingTurnStore.clear("s1", 0)
        assertNull(StreamingTurnStore.active.value)
    }

    @Test
    fun clear_ignores_a_stale_turn_or_session() {
        StreamingTurnStore.update(turn("s1", 1, "hi"))
        // An older turn's finally must not wipe the newer active turn (REPLACE overlap).
        StreamingTurnStore.clear("s1", 0)
        assertEquals(1, StreamingTurnStore.active.value?.turnIndex)
        // A different session's clear must not touch this one either.
        StreamingTurnStore.clear("s2", 1)
        assertEquals("s1", StreamingTurnStore.active.value?.sessionId)
    }
}
