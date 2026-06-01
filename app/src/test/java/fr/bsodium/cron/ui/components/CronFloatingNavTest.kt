package fr.bsodium.cron.ui.components

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import fr.bsodium.cron.ui.theme.CronTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@Suppress("DEPRECATION") // createComposeRule v1 is fine here; v2 changes dispatcher semantics.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
class CronFloatingNavTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun tapping_an_unselected_tab_navigates_to_it() {
        val navigated = mutableListOf<String>()
        composeTestRule.setContent {
            CronTheme {
                CronFloatingNav(currentRoute = "home", onNavigate = { navigated += it }, fabAction = null)
            }
        }

        composeTestRule.onNodeWithContentDescription("History").performClick()
        assertEquals(listOf("history"), navigated)
    }

    @Test
    fun selection_disables_the_current_tab() {
        composeTestRule.setContent {
            CronTheme {
                CronFloatingNav(currentRoute = "home", onNavigate = {}, fabAction = null)
            }
        }

        composeTestRule.onNodeWithContentDescription("Home").assertIsNotEnabled()
        composeTestRule.onNodeWithContentDescription("History").assertIsEnabled()
    }
}
