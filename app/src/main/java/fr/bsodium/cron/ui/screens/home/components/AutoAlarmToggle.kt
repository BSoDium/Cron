package fr.bsodium.cron.ui.screens.home.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import fr.bsodium.cron.ui.components.rememberCronHaptics
import fr.bsodium.cron.ui.theme.CronTheme
import fr.bsodium.cron.ui.theme.MaterialSymbol
import fr.bsodium.cron.ui.theme.Radius
import fr.bsodium.cron.ui.theme.Spacing
import fr.bsodium.cron.ui.theme.Symbol

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun AutoAlarmToggle(
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    hapticsEnabled: Boolean = true,
) {
    val haptics = rememberCronHaptics(enabled = hapticsEnabled)
    var initialRender by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { initialRender = false }
    val targetColor = if (enabled) {
        MaterialTheme.colorScheme.surfaceContainerHigh
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }
    val containerColor by if (initialRender) {
        remember(targetColor) { mutableStateOf(targetColor) }
    } else {
        animateColorAsState(
            targetValue = targetColor,
            animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
            label = "auto-plan-pill-color",
        )
    }
    Surface(
        color = containerColor,
        shape = Radius.full,
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            modifier = Modifier.padding(start = Spacing.lg, end = Spacing.sm, top = Spacing.sm, bottom = Spacing.sm),
        ) {
            Column {
                Text(
                    text = "Auto-plan",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = if (enabled) "Enabled" else "Disabled",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
                Switch(
                    checked = enabled,
                    onCheckedChange = { v -> haptics.contextClick(); onChange(v) },
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
    }
}

@Preview(showBackground = true, name = "Auto-plan pill — on / off")
@Composable
private fun AutoAlarmTogglePreview() {
    CronTheme {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.xl),
            modifier = Modifier.padding(Spacing.lg),
        ) {
            AutoAlarmToggle(enabled = true, onChange = {})
            AutoAlarmToggle(enabled = false, onChange = {})
        }
    }
}
