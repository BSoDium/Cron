package fr.bsodium.cron.ui.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import fr.bsodium.cron.ui.theme.CronTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@Suppress("DEPRECATION")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
class CronFloatingNavScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun home_tab_selected_with_fab() {
        composeTestRule.setContent {
            CronTheme {
                CronFloatingNav(
                    currentRoute = "home",
                    onNavigate = {},
                    fabAction = FabAction(onClick = {}),
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage()
    }

    @Test
    fun history_tab_selected_no_fab() {
        composeTestRule.setContent {
            CronTheme {
                CronFloatingNav(
                    currentRoute = "history",
                    onNavigate = {},
                    fabAction = null,
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage()
    }
}
