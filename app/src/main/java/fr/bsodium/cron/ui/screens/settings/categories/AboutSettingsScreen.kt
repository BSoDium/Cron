package fr.bsodium.cron.ui.screens.settings.categories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import fr.bsodium.cron.ui.screens.settings.components.SettingsDetailScaffold
import fr.bsodium.cron.ui.theme.CronColors
import fr.bsodium.cron.ui.theme.CronTheme
import fr.bsodium.cron.ui.theme.Radius
import fr.bsodium.cron.ui.theme.Spacing

private data class CreditEntry(val title: String, val subtitle: String, val url: String)

private val creditEntries = listOf(
    CreditEntry(
        title = "Credits",
        subtitle = "Food illustrations by Storyset",
        url = "https://storyset.com/food",
    ),
    CreditEntry(
        title = "Credits",
        subtitle = "Illustrations by yayangart",
        url = "https://pixabay.com/users/yayangart/",
    ),
)

@Composable
fun AboutSettingsScreen(onBack: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    SettingsDetailScaffold(title = "About", onBack = onBack) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            creditEntries.forEach { entry ->
                CreditRow(entry = entry, onClick = { uriHandler.openUri(entry.url) })
            }
        }
    }
}

@Composable
private fun CreditRow(entry: CreditEntry, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.lg),
        color = CronColors.elementSurface,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = entry.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "View",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = Spacing.md),
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 480, heightDp = 300, fontScale = 1.0f)
@Composable
private fun AboutSettingsScreenPreview() {
    CronTheme {
        AboutSettingsScreen(onBack = {})
    }
}
