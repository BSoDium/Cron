package fr.bsodium.cron.ui.screens.settings

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import fr.bsodium.cron.settings.SecureKeyStore
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalTime
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.time.Duration.Companion.seconds

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SettingsViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var secureStore: SecureKeyStore
    private lateinit var vm: SettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        val app = ApplicationProvider.getApplicationContext<Application>()
        secureStore = mockk(relaxed = true)
        every { secureStore.hasAnthropicKey() } returns true
        vm = SettingsViewModel(app, secureStore)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun setters_are_reflected_in_uiState() = runTest(dispatcher) {
        vm.uiState.test(timeout = 5.seconds) {
            awaitItem() // initial stateIn default
            vm.setHardLatest(LocalTime(11, 30))
            vm.setCommuteBuffer(45)
            var state = awaitItem()
            while (state.hardLatest != LocalTime(11, 30) || state.commuteBufferMinutes != 45) {
                state = awaitItem()
            }
            assertEquals(LocalTime(11, 30), state.hardLatest)
            assertEquals(45, state.commuteBufferMinutes)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun hasApiKey_reflects_secure_store() = runTest(dispatcher) {
        vm.uiState.test(timeout = 5.seconds) {
            var state = awaitItem()
            while (!state.hasApiKey) state = awaitItem()
            assertTrue(state.hasApiKey)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
