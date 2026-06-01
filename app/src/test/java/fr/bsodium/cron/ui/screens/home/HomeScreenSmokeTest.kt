package fr.bsodium.cron.ui.screens.home

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.WorkManagerTestInitHelper
import fr.bsodium.cron.FabRegistry
import fr.bsodium.cron.session.db.CronDatabase
import fr.bsodium.cron.ui.theme.CronTheme
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@Suppress("DEPRECATION") // createComposeRule v1 is fine here; v2 changes dispatcher semantics.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
class HomeScreenSmokeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var app: Application

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(app)
        runBlocking { CronDatabase.get(app).sessionDao().deleteOlderThan(Long.MAX_VALUE) }
    }

    @Test
    fun home_screen_composes_with_its_viewModel() {
        composeTestRule.mainClock.autoAdvance = false
        val viewModel = HomeViewModel(app)
        val fabRegistry = FabRegistry()
        composeTestRule.setContent {
            CronTheme {
                HomeScreen(viewModel = viewModel, fabRegistry = fabRegistry)
            }
        }
        composeTestRule.onRoot().assertExists()
    }
}
