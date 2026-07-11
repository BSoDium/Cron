package fr.bsodium.cron.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/** `Symbol()` bakes its variation axes into a cached `Typeface` variant rather than a live per-Paint
 *  override (see docs/color-roles.md) — this covers the two glyphs reported off-center on-device
 *  (`AlarmOff`, `NotificationImportant`, both compound bell-based icons) at the exact size the
 *  timeline uses them, alongside the two that were already correct (`PlayArrow`, `Bedtime`) as a
 *  no-regression control. */
@Suppress("DEPRECATION")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
class MaterialSymbolsScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun timeline_glyphs_sit_centered_in_their_dot_at_icon_size() {
        composeTestRule.setContent {
            CronTheme {
                Row(
                    modifier = Modifier.padding(Spacing.lg),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    listOf(
                        MaterialSymbol.PlayArrow,
                        MaterialSymbol.Bedtime,
                        MaterialSymbol.AlarmOff,
                        MaterialSymbol.NotificationImportant,
                    ).forEach { symbol ->
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                            contentAlignment = Alignment.Center,
                        ) {
                            Symbol(
                                symbol = symbol,
                                contentDescription = symbol.name,
                                size = 14.dp,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage()
    }
}
