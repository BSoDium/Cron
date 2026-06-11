package fr.bsodium.cron.ui.screens.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import fr.bsodium.cron.ui.components.rememberCronHaptics
import fr.bsodium.cron.ui.theme.CronTheme
import fr.bsodium.cron.ui.theme.MaterialSymbol
import fr.bsodium.cron.ui.theme.Spacing
import fr.bsodium.cron.ui.theme.Symbol

/**
 * On/off switch for automatic alarm planning, with an alarm icon in the thumb. Lives at the right end
 * of the home greeting row.
 */
@Composable
internal fun AutoAlarmToggle(
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    hapticsEnabled: Boolean = true,
) {
    val haptics = rememberCronHaptics(enabled = hapticsEnabled)
    // Opt out of the 48dp minimum-interactive reservation so the switch lays out at its natural ~32dp
    // height and doesn't inflate the greeting row (which would push the alarm card down).
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
        Switch(
            checked = enabled,
            onCheckedChange = { v -> haptics.contextClick(); onChange(v) },
            modifier = modifier,
            thumbContent = {
                Symbol(
                    symbol = if (enabled) MaterialSymbol.Alarm else MaterialSymbol.AlarmOff,
                    contentDescription = if (enabled) "Auto alarms on" else "Auto alarms off",
                    size = SwitchDefaults.IconSize,
                )
            },
        )
    }
}

@Preview(showBackground = true, name = "Auto alarms — on / off")
@Composable
private fun AutoAlarmTogglePreview() {
    CronTheme {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xl), modifier = Modifier.padding(Spacing.lg)) {
            AutoAlarmToggle(enabled = true, onChange = {})
            AutoAlarmToggle(enabled = false, onChange = {})
        }
    }
}
