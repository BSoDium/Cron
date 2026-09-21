package fr.bsodium.cron.ui.screens.home.components

import android.content.res.Configuration
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.bsodium.cron.ui.components.CronIllustration
import fr.bsodium.cron.ui.components.CronIllustrationType
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
        CronIllustration(
            type = CronIllustrationType.Flowers2,
            modifier = Modifier.size(220.dp)
        )
        Spacer(Modifier.height(Spacing.xxl))
        Text(
            text = "Let's get started",
            style = CronTypography.bodySerif.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 24.sp,
                lineHeight = 30.sp,
            ),
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Spacing.md))
        Text(
            text = "Cron reads your calendar and last night's sleep to pick the " +
                "smartest wake-up time. Run it to plan your morning.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
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
