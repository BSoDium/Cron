package fr.bsodium.cron.ui.screens.settings

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.TopAppBarState
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import com.github.takahirom.roborazzi.captureRoboImage
import fr.bsodium.cron.ui.theme.CronTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Suppress("DEPRECATION")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
class SettingsScreenScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    /** Regression coverage for a real bug: hoisting `TopAppBarState` with `initialHeightOffsetLimit =
     *  0f` (instead of the library's own `rememberTopAppBarState()` default, `-Float.MAX_VALUE`) left
     *  the collapsed small title permanently invisible — see `docs/compose-gotchas.md`. */
    @Test
    fun collapsed_title_stays_visible_when_scrolled() {
        val listState = LazyListState()
        val topAppBarState = TopAppBarState(-Float.MAX_VALUE, 0f, 0f)
        composeTestRule.setContent {
            CronTheme {
                SettingsScreen(onOpenCategory = {}, listState = listState, topAppBarState = topAppBarState)
            }
        }
        composeTestRule.onRoot().performTouchInput {
            swipeUp(startY = bottom - 50f, endY = top + 50f)
        }
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage()
    }
}
