package fr.bsodium.cron.ui.screens.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import fr.bsodium.cron.ui.theme.CronTheme
import fr.bsodium.cron.ui.theme.CronTypography
import fr.bsodium.cron.ui.theme.Spacing

/**
 * One-line time-of-day greeting with a bolded user name; ellipsises when it shares its row with the
 * auto-alarms toggle on narrow screens.
 *
 *   Good morning, **Elliot**
 */
@Composable
fun GreetingHeader(
    prefix: String,
    name: String?,
    modifier: Modifier = Modifier,
) {
    Text(
        modifier = modifier.fillMaxWidth(),
        text = buildAnnotatedString {
            append(prefix)
            if (!name.isNullOrBlank()) {
                append(", ")
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(name)
                }
            }
        },
        style = CronTypography.greeting,
        color = MaterialTheme.colorScheme.onBackground,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
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
        HomeGreetingRow(prefix = "Good morning", name = "Elliot", autoAlarmsEnabled = true, onAutoAlarmsChange = {})
    }
}
