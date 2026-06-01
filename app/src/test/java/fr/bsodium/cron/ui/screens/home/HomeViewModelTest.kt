package fr.bsodium.cron.ui.screens.home

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.WorkManagerTestInitHelper
import app.cash.turbine.test
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
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

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
}
