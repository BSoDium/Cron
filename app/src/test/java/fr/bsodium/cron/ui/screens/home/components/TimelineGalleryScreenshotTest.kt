@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package fr.bsodium.cron.ui.screens.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import fr.bsodium.cron.ui.screens.home.TimelineItem
import fr.bsodium.cron.ui.theme.CronColors
import fr.bsodium.cron.ui.theme.CronTheme
import fr.bsodium.cron.ui.theme.Spacing
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** One-off verification that the gallery previews (`TimelineEventGalleryPreviews.kt`,
 *  `AiRunHeroGalleryPreviews.kt`) actually render as intended, in both themes — a `@Preview`
 *  function only gets eyeballed in Android Studio's tooling otherwise, which this suite doesn't run
 *  in. Robolectric's `+night`/`+notnight` qualifiers pick the theme, mirroring what
 *  `@PreviewLightDark` does for the IDE. */
@Suppress("DEPRECATION")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
class TimelineGalleryScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Config(qualifiers = "+notnight")
    @Test
    fun event_cap_gallery_light() {
        composeTestRule.setContent { EventCapGalleryPreview() }
        composeTestRule.onRoot().captureRoboImage()
    }

    @Config(qualifiers = "+night")
    @Test
    fun event_cap_gallery_dark() {
        composeTestRule.setContent { EventCapGalleryPreview() }
        composeTestRule.onRoot().captureRoboImage()
    }

    @Config(qualifiers = "+notnight")
    @Test
    fun event_interior_gallery_light() {
        composeTestRule.setContent { EventInteriorGalleryPreview() }
        composeTestRule.onRoot().captureRoboImage()
    }

    @Config(qualifiers = "+night")
    @Test
    fun event_interior_gallery_dark() {
        composeTestRule.setContent { EventInteriorGalleryPreview() }
        composeTestRule.onRoot().captureRoboImage()
    }

    @Config(qualifiers = "+notnight")
    @Test
    fun ai_run_hero_gallery_light() {
        composeTestRule.setContent { AiRunHeroGalleryPreview() }
        composeTestRule.onRoot().captureRoboImage()
    }

    @Config(qualifiers = "+night")
    @Test
    fun ai_run_hero_gallery_dark() {
        composeTestRule.setContent { AiRunHeroGalleryPreview() }
        composeTestRule.onRoot().captureRoboImage()
    }

    /** `EventCapGalleryPreview`'s sleep-track section renders below the fold in a fixed-height
     *  Robolectric window (its `Column` has no scroll), so `event_cap_gallery_*` above never
     *  exercises it — these two isolate that section alone to verify the `inverseSurface` escape
     *  hatch ([TriggerVisuals] `capContainerColor`) against every accent. */
    @Config(qualifiers = "+notnight")
    @Test
    fun cap_gallery_sleep_track_light() {
        composeTestRule.setContent {
            CronTheme {
                Column(modifier = Modifier.fillMaxSize().background(CronColors.pageBackground)) {
                    EventGalleryTrackBlock(sectionLabel = "Cap anchors — sleep track", isAsleep = true, atCap = true)
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage()
    }

    @Config(qualifiers = "+night")
    @Test
    fun cap_gallery_sleep_track_dark() {
        composeTestRule.setContent {
            CronTheme {
                Column(modifier = Modifier.fillMaxSize().background(CronColors.pageBackground)) {
                    EventGalleryTrackBlock(sectionLabel = "Cap anchors — sleep track", isAsleep = true, atCap = true)
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage()
    }

    /** Every header renders identically (see [DayHeaderRow]'s KDoc) — renders two consecutive
     *  headers so spacing/divider/label rendering stays visually checked in both themes. */
    @Config(qualifiers = "+notnight")
    @Test
    fun day_header_gallery_light() {
        composeTestRule.setContent { DayHeaderGallery() }
        composeTestRule.onRoot().captureRoboImage()
    }

    @Config(qualifiers = "+night")
    @Test
    fun day_header_gallery_dark() {
        composeTestRule.setContent { DayHeaderGallery() }
        composeTestRule.onRoot().captureRoboImage()
    }
}

@Composable
private fun DayHeaderGallery() {
    CronTheme {
        // Real usage always sits inside HomeContent.kt's LazyColumn contentPadding — matching it here so the big date number isn't rendered flush against the screen edge, which reads as clipped in a screenshot.
        Column(modifier = Modifier.fillMaxSize().background(CronColors.pageBackground).padding(horizontal = Spacing.md)) {
            DayHeaderRow(
                item = TimelineItem.DayHeader(date = LocalDate(2026, 7, 3), timestamp = Instant.fromEpochMilliseconds(0)),
            )
            DayHeaderRow(
                item = TimelineItem.DayHeader(date = LocalDate(2026, 7, 2), timestamp = Instant.fromEpochMilliseconds(0)),
            )
        }
    }
}
