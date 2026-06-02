package fr.bsodium.cron.ui.screens.home.components

import android.content.res.Configuration
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import fr.bsodium.cron.R
import fr.bsodium.cron.ui.components.recolored
import fr.bsodium.cron.ui.theme.CronTheme
import fr.bsodium.cron.ui.theme.CronTypography
import fr.bsodium.cron.ui.theme.Spacing

/**
 * First-run onboarding: an illustration, a serif invitation, and a line explaining what a plan
 * needs. The play FAB (pointed at by the onboarding callout) is the CTA.
 */
@Composable
internal fun OnboardingHint(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val scheme = MaterialTheme.colorScheme
        val source = ImageVector.vectorResource(R.drawable.ic_onboarding_illustration)
        // Tonal ramp around the dynamic accent: highlights blend toward white, shadows toward black so
        // they stay lighter/darker than the body in both themes (Material role pairs would invert).
        // Dark mode flips `primary` to a light pastel, and the fallback accent is itself low-chroma. Build the
        // dark body in HSL — keep the accent hue, floor its saturation so the layers read as *tinted* (not grey),
        // and set a deep lightness so they stay darker than the near-white frosting. Light mode is untouched.
        val dark = isSystemInDarkTheme()
        val body = if (dark) {
            val hsl = FloatArray(3)
            ColorUtils.colorToHSL(scheme.primary.toArgb(), hsl)
            Color.hsl(hsl[0], hsl[1].coerceAtLeast(CAKE_DARK_SAT_FLOOR), CAKE_DARK_BODY_LIGHTNESS)
        } else {
            scheme.primary
        }
        val highlight = lerp(scheme.primary, Color.White, CAKE_HIGHLIGHT_TINT)
        val accent = lerp(scheme.primary, Color.Black, CAKE_ACCENT_TINT)
        val shadow = lerp(scheme.primary, Color.Black, CAKE_SHADOW_TINT)
        val ground = scheme.surfaceVariant
        val themedCake = remember(source, body, highlight, accent, shadow, ground) {
            source.recolored { original ->
                when (original) {
                    CAKE_BODY -> body
                    CAKE_HIGHLIGHT -> highlight
                    CAKE_GROUND -> ground
                    CAKE_ACCENT -> accent
                    CAKE_SHADOW -> shadow
                    else -> original
                }
            }
        }
        Image(
            imageVector = themedCake,
            contentDescription = null,
            modifier = Modifier.size(200.dp),
        )
        Spacer(Modifier.height(Spacing.lg))
        Text(
            text = "Let's get started",
            style = CronTypography.bodySerif.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 30.sp,
                lineHeight = 36.sp,
            ),
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Spacing.sm))
        Text(
            text = "Cron reads your calendar and last night's sleep to pick the " +
                "smartest wake-up time. Run it to plan your morning.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** Source fills of `ic_onboarding_illustration`, remapped onto `colorScheme` so the cake tracks Material You. */
private val CAKE_BODY = Color(0xFF407BFF)
private val CAKE_HIGHLIGHT = Color(0xFFFFFFFF)
private val CAKE_GROUND = Color(0xFFF5F5F5)
private val CAKE_ACCENT = Color(0xFF263238)
private val CAKE_SHADOW = Color(0xFF000000)

/** Blend fractions for the cake's tonal ramp: highlight toward white, accent/shadow toward black. */
private const val CAKE_HIGHLIGHT_TINT = 0.82f
private const val CAKE_ACCENT_TINT = 0.50f
private const val CAKE_SHADOW_TINT = 0.60f

/** Dark-theme only: the cake body uses the accent hue at this saturation floor + lightness, so the layers read
 *  as a deep *tinted* accent (not grey) while staying darker than the near-white frosting. */
private const val CAKE_DARK_SAT_FLOOR = 0.45f
private const val CAKE_DARK_BODY_LIGHTNESS = 0.35f

@Preview(name = "Onboarding hint — light", showBackground = true)
@Preview(name = "Onboarding hint — dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun OnboardingHintPreview() {
    CronTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            OnboardingHint(Modifier.padding(Spacing.xxl))
        }
    }
}
