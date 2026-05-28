package fr.bsodium.cron.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import fr.bsodium.cron.ui.theme.CronTypography

/**
 * Page title for the tab screens — same face/position as the home greeting,
 * standing in for a top app bar.
 */
@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier.fillMaxWidth(),
        style = CronTypography.pageTitle,
        color = MaterialTheme.colorScheme.onBackground,
    )
}
