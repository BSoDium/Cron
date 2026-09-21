package fr.bsodium.cron.ui.screens.home.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.bsodium.cron.ui.components.CronIllustratedMessage
import fr.bsodium.cron.ui.components.CronIllustrationType
import fr.bsodium.cron.ui.theme.CronTheme
import fr.bsodium.cron.ui.theme.Spacing

private val IllustrationSize = 220.dp

/**
 * First-run onboarding: an illustration, a serif invitation, and a line explaining what a plan
 * needs. The play FAB (pointed at by the onboarding callout) is the CTA.
 */
@Composable
internal fun OnboardingHint(modifier: Modifier = Modifier) {
    CronIllustratedMessage(
        type = CronIllustrationType.Flowers2,
        title = "Let's get started",
        subtitle = "Cron reads your calendar and last night's sleep to pick the " +
            "smartest wake-up time. Run it to plan your morning.",
        illustrationSize = IllustrationSize,
        modifier = modifier,
    )
}

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
