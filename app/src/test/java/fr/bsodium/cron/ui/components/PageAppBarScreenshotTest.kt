package fr.bsodium.cron.ui.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarState
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import fr.bsodium.cron.ui.theme.CronTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/** #183: a long title must ellipsize, not overflow past the bar, at both scroll extremes. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Suppress("DEPRECATION")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
class PageAppBarScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val longTitle = "This is a deliberately long AI-generated title meant to overflow the app bar"

    @Test
    fun subtitle_is_visible_expanded() {
        composeTestRule.setContent {
            CronTheme {
                val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        PageAppBar(
                            title = "Top 10 hiking trails",
                            subtitle = "Discover popular trails",
                            scrollBehavior = scrollBehavior,
                            onBack = {},
                        )
                    },
                ) { inner -> Text("body", modifier = Modifier.padding(inner)) }
            }
        }
        composeTestRule.onRoot().captureRoboImage()
    }

    @Test
    fun subtitle_is_visible_collapsed() {
        composeTestRule.setContent {
            CronTheme {
                val state = TopAppBarState(
                    initialHeightOffsetLimit = -500f,
                    initialHeightOffset = -500f,
                    initialContentOffset = 0f,
                )
                val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(state = state)
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        PageAppBar(
                            title = "Top 10 hiking trails",
                            subtitle = "Discover popular trails",
                            scrollBehavior = scrollBehavior,
                            onBack = {},
                        )
                    },
                ) { inner -> Text("body", modifier = Modifier.padding(inner)) }
            }
        }
        composeTestRule.onRoot().captureRoboImage()
    }

    @Test
    fun long_title_ellipsizes_expanded() {
        composeTestRule.setContent {
            CronTheme {
                val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = { PageAppBar(title = longTitle, scrollBehavior = scrollBehavior, onBack = {}) },
                ) { inner -> Text("body", modifier = Modifier.padding(inner)) }
            }
        }
        composeTestRule.onRoot().captureRoboImage()
    }

    @Test
    fun long_title_ellipsizes_collapsed() {
        composeTestRule.setContent {
            CronTheme {
                val state = TopAppBarState(initialHeightOffsetLimit = -500f, initialHeightOffset = -500f, initialContentOffset = 0f)
                val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(state = state)
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = { PageAppBar(title = longTitle, scrollBehavior = scrollBehavior, onBack = {}) },
                ) { inner -> Text("body", modifier = Modifier.padding(inner)) }
            }
        }
        composeTestRule.onRoot().captureRoboImage()
    }
}
