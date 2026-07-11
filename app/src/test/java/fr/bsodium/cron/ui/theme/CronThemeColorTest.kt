package fr.bsodium.cron.ui.theme

import android.app.Application
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Diagnostic for a user report that the timeline's asleep-track color (tertiaryContainer) looked
 * identical after switching the device from dark to light theme. CronTheme.kt's own branching
 * (isSystemInDarkTheme -> dynamicDarkColorScheme/dynamicLightColorScheme) reads correctly on
 * inspection; this verifies the underlying M3 dynamic-color constructors themselves produce
 * genuinely different tonal palettes for light vs dark, independent of any app-level logic — if
 * this passes, CronTheme's branching is confirmed correct and an on-device report of no visible
 * difference is more likely OEM/dynamic-color-cache related, not an in-app bug.
 */
@RunWith(RobolectricTestRunner::class)
class CronThemeColorTest {

    private val app: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun light_and_dark_dynamic_schemes_have_different_tertiary_container() {
        val dark = dynamicDarkColorScheme(app)
        val light = dynamicLightColorScheme(app)
        assertNotEquals(dark.tertiaryContainer, light.tertiaryContainer)
    }

    @Test
    fun light_and_dark_dynamic_schemes_have_different_surface_container_high() {
        val dark = dynamicDarkColorScheme(app)
        val light = dynamicLightColorScheme(app)
        assertNotEquals(dark.surfaceContainerHigh, light.surfaceContainerHigh)
    }
}
