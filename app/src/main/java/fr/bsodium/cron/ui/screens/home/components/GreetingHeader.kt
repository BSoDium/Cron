package fr.bsodium.cron.ui.screens.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import fr.bsodium.cron.ui.theme.CronTheme
import fr.bsodium.cron.ui.theme.CronTypography
import fr.bsodium.cron.ui.theme.LcdFontFamily
import fr.bsodium.cron.ui.theme.Spacing

@Composable
fun HomeGreetingRow(
    prefix: String,
    name: String?,
    autoAlarmsEnabled: Boolean,
    onAutoAlarmsChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    hapticsEnabled: Boolean = true,
) {
    val nameStyle = CronTypography.greetingName.copy(fontFamily = LcdFontFamily)
    Row(
        modifier = modifier.fillMaxWidth().height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Column(modifier = Modifier.weight(1f)) {
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
                style = nameStyle,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        AutoAlarmToggle(
            enabled = autoAlarmsEnabled,
            onChange = onAutoAlarmsChange,
            modifier = Modifier.fillMaxHeight(),
            hapticsEnabled = hapticsEnabled,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun GreetingHeaderPreview() {
    CronTheme {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
            HomeGreetingRow(prefix = "Good morning", name = "Elliot", autoAlarmsEnabled = true, onAutoAlarmsChange = {})
            HomeGreetingRow(prefix = "Good evening", name = "Maximilian-Alexander", autoAlarmsEnabled = false, onAutoAlarmsChange = {})
        }
    }
}
