@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package fr.bsodium.cron.ui.screens.home.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import fr.bsodium.cron.ui.theme.CronTheme
import fr.bsodium.cron.ui.theme.CronTypography
import fr.bsodium.cron.ui.theme.MaterialSymbol
import fr.bsodium.cron.ui.theme.Spacing
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@Suppress("DEPRECATION")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
class TimelineNodeScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun mixed_anchor_states() {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            CronTheme {
                val secondaryColor = MaterialTheme.colorScheme.onSurfaceVariant
                Column(modifier = Modifier.padding(horizontal = Spacing.lg)) {
                    TimelineNode(
                        anchor = TimelineAnchor.Loader,
                        isFirst = true,
                        isLast = false,
                        emphasized = true,
                        title = { Text("Replanning", style = MaterialTheme.typography.labelLarge) },
                        status = {
                            Text("Latest · 07:16", style = MaterialTheme.typography.labelSmall, color = secondaryColor)
                        },
                    )
                    TimelineNode(
                        anchor = TimelineAnchor.Icon(MaterialSymbol.Snooze, tint = secondaryColor),
                        isFirst = false,
                        isLast = false,
                        title = { Text("Alarm snoozed", style = MaterialTheme.typography.bodyMedium, color = secondaryColor) },
                        status = {
                            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                                MonoPill("9 min")
                                Text("07:15", style = CronTypography.labelMonoSmall, color = secondaryColor)
                            }
                        },
                    )
                    TimelineNode(
                        anchor = TimelineAnchor.Icon(MaterialSymbol.Schedule),
                        isFirst = false,
                        isLast = false,
                        onClick = {},
                        title = { Text("Scheduled plan", style = MaterialTheme.typography.bodyMedium) },
                        status = { Text("23:14", style = CronTypography.labelMonoSmall, color = secondaryColor) },
                    )
                    TimelineNode(
                        anchor = TimelineAnchor.Plain,
                        isFirst = false,
                        isLast = false,
                        title = { Text("You fell asleep", style = MaterialTheme.typography.bodyMedium, color = secondaryColor) },
                        status = { Text("23:40", style = CronTypography.labelMonoSmall, color = secondaryColor) },
                    )
                    TimelineNode(
                        anchor = TimelineAnchor.Icon(MaterialSymbol.Bedtime, tint = secondaryColor),
                        isFirst = false,
                        isLast = true,
                        title = { Text("You fell asleep", style = MaterialTheme.typography.bodyMedium, color = secondaryColor) },
                        status = { Text("22:30", style = CronTypography.labelMonoSmall, color = secondaryColor) },
                    )
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage()
    }
}
