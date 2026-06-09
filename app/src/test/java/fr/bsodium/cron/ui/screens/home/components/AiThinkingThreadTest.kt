package fr.bsodium.cron.ui.screens.home.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import fr.bsodium.cron.ui.screens.home.AiThreadUi
import fr.bsodium.cron.ui.screens.home.ProcessItem
import fr.bsodium.cron.ui.theme.CronTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@Suppress("DEPRECATION") // createComposeRule v1 is fine here; v2 changes dispatcher semantics.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
class AiThinkingThreadTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val thread = AiThreadUi(
        turnIndex = 0,
        summary = "Setting your alarm",
        process = listOf(ProcessItem.Tool(name = "set_alarm", isComplete = true, contextLabel = "set for 06:40")),
        response = "Alarm set for the morning.",
        durationSeconds = 12,
    )

    @Test
    fun tapping_the_disclosure_toggles_expand_collapse() {
        composeTestRule.setContent {
            CronTheme {
                AiThinkingThread(thread = thread)
            }
        }

        // Settled + collapsed → the chevron offers "Expand"; tapping it flips to "Collapse".
        composeTestRule.onNodeWithContentDescription("Expand").assertExists()
        composeTestRule.onNodeWithContentDescription("Expand").performClick()
        composeTestRule.onNodeWithContentDescription("Collapse").assertExists()
    }
}
