package fr.bsodium.cron.ui.screens.home

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.WorkManagerTestInitHelper
import app.cash.turbine.test
import fr.bsodium.cron.ai.StreamingTurn
import fr.bsodium.cron.ai.StreamingTurnStore
import fr.bsodium.cron.ai.wire.ContentBlock
import fr.bsodium.cron.session.db.AiMessageEntity
import fr.bsodium.cron.session.db.CronDatabase
import fr.bsodium.cron.session.db.SessionJson
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
import kotlinx.serialization.encodeToString
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
        // The production CronDatabase singleton is file-backed and persists across tests in the JVM; wipe it (cascades to events + ai_messages) so each test starts from a clean slate.
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

    private fun aiTurnRow(sessionId: String, turn: Int, createdAt: Long, text: String) = AiMessageEntity(
        sessionId = sessionId,
        turnIndex = turn,
        role = "assistant",
        contentJson = SessionJson.encodeToString<List<ContentBlock>>(listOf(ContentBlock.Text(text))),
        createdAt = createdAt,
    )

    @Test
    fun initializes_to_empty_state_without_a_session() = runTest(dispatcher) {
        HomeViewModel(app).uiState.test(timeout = 5.seconds) {
            var state = awaitItem()
            while (!state.initialized) state = awaitItem()
            assertNull(state.sessionDisplay)
            assertNull(state.aiPlan)
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

            // A turn for this session starts streaming; the answer is marked with SUMMARY (the model's convention) so the streamed text is revealed as the answer rather than held as narration.
            StreamingTurnStore.update(
                StreamingTurn(
                    sessionId = "s1",
                    turnIndex = 0,
                    blocks = listOf(ContentBlock.Text("SUMMARY: Streaming\n\nStreaming answer…")),
                    startedAtMs = 0L,
                ),
            )
            while (state.aiPlan?.iterations?.lastOrNull()?.thread?.response == null) state = awaitItem()
            val thread = requireNotNull(state.aiPlan).iterations.last().thread
            assertEquals("Streaming answer…", thread.response)
            assertTrue(thread.isStreaming)
            assertTrue(state.isRetrying) // running spinner tracks the live stream, not just WorkManager

            // Turn ends: with no persisted rows the plan falls back to the (empty) DB state.
            StreamingTurnStore.clear("s1", 0)
            while (state.aiPlan != null) state = awaitItem()
            assertNull(state.aiPlan)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun newlyArrivedIds_is_empty_on_first_emission_then_marks_only_a_later_turn() = runTest(dispatcher) {
        val db = CronDatabase.get(app)
        db.sessionDao().insert(Fixtures.session(id = "s1", date = LocalDate.parse("2026-05-22")).toEntity())
        db.aiMessageDao().insert(aiTurnRow(sessionId = "s1", turn = 0, createdAt = 1_000L, text = "SUMMARY: first\n\nFirst answer."))

        HomeViewModel(app).uiState.test(timeout = 5.seconds) {
            var state = awaitItem()
            while (state.timeline.none { it.id == "ai-s1-0" }) state = awaitItem()
            // The very first real emission of a cold-started ViewModel must never mark anything as newly-arrived, even though "ai-s1-0" is appearing on screen for the first time — there is no reference point yet.
            assertTrue(state.newlyArrivedIds.isEmpty())

            db.aiMessageDao().insert(aiTurnRow(sessionId = "s1", turn = 1, createdAt = 2_000L, text = "SUMMARY: second\n\nSecond answer."))
            while (state.timeline.none { it.id == "ai-s1-1" }) state = awaitItem()
            assertEquals(setOf("ai-s1-1"), state.newlyArrivedIds)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
