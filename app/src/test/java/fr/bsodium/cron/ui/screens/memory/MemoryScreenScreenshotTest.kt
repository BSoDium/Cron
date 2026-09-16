package fr.bsodium.cron.ui.screens.memory

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import com.github.takahirom.roborazzi.captureRoboImage
import fr.bsodium.cron.memory.MemoryEntry
import fr.bsodium.cron.ui.theme.CronTheme
import kotlinx.datetime.Clock
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Suppress("DEPRECATION")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
class MemoryScreenScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val now = Clock.System.now()
    private val sampleEntries = listOf(
        MemoryEntry(
            id = 1,
            text = "Prefers earlier wake-ups on gym days",
            category = "schedule",
            createdAt = now - 23.hours - 59.minutes,
            updatedAt = now,
        ),
        MemoryEntry(id = 2, text = "Commutes by bike", category = null, createdAt = now - 20.minutes, updatedAt = now),
    )

    @Test
    fun with_entries() {
        composeTestRule.setContent {
            CronTheme {
                MemoryContent(entries = sampleEntries, isMutating = false, onSend = {}, onDelete = {})
            }
        }
        composeTestRule.onRoot().captureRoboImage()
    }

    @Test
    fun empty_state() {
        composeTestRule.setContent {
            CronTheme {
                MemoryContent(entries = emptyList(), isMutating = false, onSend = {}, onDelete = {})
            }
        }
        composeTestRule.onRoot().captureRoboImage()
    }

    @Test
    fun mutating_shows_pending_row_and_disables_composer() {
        composeTestRule.setContent {
            CronTheme {
                MemoryContent(entries = sampleEntries, isMutating = true, onSend = {}, onDelete = {})
            }
        }
        composeTestRule.onRoot().captureRoboImage()
    }

    /** Regression coverage for the same collapsed-title bug fixed in SettingsScreenScreenshotTest —
     *  Memory shares PageAppBar, so it must stay collapse-safe too. */
    @Test
    fun collapsed_title_stays_visible_when_scrolled() {
        composeTestRule.setContent {
            CronTheme {
                MemoryContent(entries = sampleEntries, isMutating = false, onSend = {}, onDelete = {})
            }
        }
        repeat(10) {
            composeTestRule.onRoot().performTouchInput { swipeUp(startY = bottom - 50f, endY = top + 50f) }
            composeTestRule.waitForIdle()
        }
        composeTestRule.onRoot().captureRoboImage()
    }

    /** Tapping the collapsed FAB must fade in the full-screen composer, dimming the list to the
     *  page background underneath rather than leaving it visible. */
    @Test
    fun tapping_fab_expands_to_full_screen_composer() {
        composeTestRule.setContent {
            CronTheme {
                MemoryContent(entries = sampleEntries, isMutating = false, onSend = {}, onDelete = {})
            }
        }
        composeTestRule.onNodeWithContentDescription("Tell Cron something to remember").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage()
    }

    /** Mid-swipe: the trash icon should be tracking the center of the still-narrow reveal, not
     *  pinned to the row's edge — Gmail-style reveal physics, not a static background icon. */
    @Test
    fun swipe_in_progress_shows_reveal_physics() {
        composeTestRule.setContent {
            CronTheme {
                MemoryContent(entries = sampleEntries, isMutating = false, onSend = {}, onDelete = {})
            }
        }
        composeTestRule.onNodeWithTag("memory-entry-2").performTouchInput {
            down(centerRight - Offset(4f, 0f))
            moveBy(Offset(-120f, 0f))
        }
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage()
    }
}
