package fr.bsodium.cron.ui.screens.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import fr.bsodium.cron.ui.theme.CronTheme
import fr.bsodium.cron.ui.theme.CronTypography
import fr.bsodium.cron.ui.theme.Spacing

/**
 * Two-line time-of-day greeting: a muted prefix stacked over the user's name, which gets its own
 * full-width line so longer names don't contend with the auto-alarms toggle. With no name, the prefix
 * becomes the single prominent line.
 *
 *   Good evening,
 *   **Elliot**
 */
@Composable
fun GreetingHeader(
    prefix: String,
    name: String?,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (name.isNullOrBlank()) {
            Text(
                text = prefix,
                style = CronTypography.greetingName,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            Text(
                text = "$prefix,",
                style = CronTypography.greetingPrefix,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = name,
                style = CronTypography.greetingName,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Top-of-home row: the greeting on the left (its original spot), the auto-alarms switch at the end. */
@Composable
fun HomeGreetingRow(
    prefix: String,
    name: String?,
    autoAlarmsEnabled: Boolean,
    onAutoAlarmsChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        GreetingHeader(prefix = prefix, name = name, modifier = Modifier.weight(1f))
        AutoAlarmToggle(enabled = autoAlarmsEnabled, onChange = onAutoAlarmsChange)
    }
}

@Preview(showBackground = true)
@Composable
private fun GreetingHeaderPreview() {
    CronTheme {
        androidx.compose.foundation.layout.Column(
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            HomeGreetingRow(prefix = "Good morning", name = "Elliot", autoAlarmsEnabled = true, onAutoAlarmsChange = {})
            HomeGreetingRow(prefix = "Good evening", name = "Maximilian-Alexander", autoAlarmsEnabled = false, onAutoAlarmsChange = {})
        }
    }
}
