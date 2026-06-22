package fr.bsodium.cron.ui.screens.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import fr.bsodium.cron.ui.theme.CronTheme
import fr.bsodium.cron.ui.theme.CronTypography
import fr.bsodium.cron.ui.theme.Spacing

@Composable
fun HomeGreetingRow(
    prefix: String,
    name: String?,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (!name.isNullOrBlank()) {
            Text(
                text = "$prefix,",
                style = CronTypography.greetingPrefix,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = name?.takeIf { it.isNotBlank() } ?: prefix,
            style = CronTypography.greetingName,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun GreetingHeaderPreview() {
    CronTheme {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
            HomeGreetingRow(prefix = "Good morning", name = "Elliot")
            HomeGreetingRow(prefix = "Good evening", name = "Maximilian-Alexander")
        }
    }
}
