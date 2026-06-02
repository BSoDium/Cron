package fr.bsodium.cron.ui.screens.home

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.WorkManagerTestInitHelper
import app.cash.turbine.test
import fr.bsodium.cron.ai.StreamingTurn
import fr.bsodium.cron.ai.StreamingTurnStore
import fr.bsodium.cron.ai.wire.ContentBlock
import fr.bsodium.cron.session.db.CronDatabase
import fr.bsodium.cron.session.db.toEntity
import fr.bsodium.cron.session.model.ActionType
import fr.bsodium.cron.testutil.Fixtures
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class HomeViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var app: Application

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        app = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(app)
        // The production CronDatabase singleton is file-backed and persists across tests in the JVM;
        // wipe it (cascades to events + ai_messages) so each test starts from a clean slate.
        runBlocking { CronDatabase.get(app).sessionDao().deleteOlderThan(Long.MAX_VALUE) }
        resetStreamingStore()
    }

    @After
    fun tearDown() {
        resetStreamingStore()
        Dispatchers.resetMain()
    }

    /** The streaming store is a process-wide singleton — clear it so it can't leak across tests. */
    private fun resetStreamingStore() {
        StreamingTurnStore.active.value?.let { StreamingTurnStore.clear(it.sessionId, it.turnIndex) }
    }

    @Test
    fun initializes_to_empty_state_without_a_session() = runTest(dispatcher) {
        HomeViewModel(app).uiState.test(timeout = 5.seconds) {
            var state = awaitItem()
            while (!state.initialized) state = awaitItem()
            assertNull(state.sessionDisplay)
            assertNull(state.aiThread)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun reflects_a_persisted_session_in_uiState() = runTest(dispatcher) {
        CronDatabase.get(app).sessionDao().insert(
            Fixtures.session(
                id = "s1",
                date = LocalDate.parse("2026-05-22"),
                currentInstruction = Fixtures.instruction(action = ActionType.SetAlarm, alarmTime = LocalTime(6, 40)),
            ).toEntity(),
        )

        HomeViewModel(app).uiState.test(timeout = 5.seconds) {
            var state = awaitItem()
            while (state.sessionDisplay == null) state = awaitItem()
            val display = state.sessionDisplay
            assertEquals(ActionType.SetAlarm, display.action)
            assertEquals(LocalTime(6, 40), display.alarmTime)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun streaming_partial_overrides_db_thread_and_marks_running_then_falls_back() = runTest(dispatcher) {
        CronDatabase.get(app).sessionDao().insert(
            Fixtures.session(id = "s1", date = LocalDate.parse("2026-05-22")).toEntity(),
        )

        HomeViewModel(app).uiState.test(timeout = 5.seconds) {
            var state = awaitItem()
            while (state.sessionDisplay == null) state = awaitItem()

            // A turn for this session starts streaming. The answer is marked with SUMMARY (the model's
            // convention) so the streamed text is revealed as the answer rather than held as narration.
            StreamingTurnStore.update(
                StreamingTurn(
                    sessionId = "s1",
                    turnIndex = 0,
                    blocks = listOf(ContentBlock.Text("SUMMARY: Streaming\n\nStreaming answer…")),
                    startedAtMs = 0L,
                ),
            )
            while (state.aiThread?.response == null) state = awaitItem()
            val thread = requireNotNull(state.aiThread)
            assertEquals("Streaming answer…", thread.response)
            assertTrue(thread.isStreaming)
            assertTrue(state.isRetrying) // running spinner tracks the live stream, not just WorkManager

            // Turn ends: with no persisted rows the thread falls back to the (empty) DB state.
            StreamingTurnStore.clear("s1", 0)
            while (state.aiThread != null) state = awaitItem()
            assertNull(state.aiThread)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
