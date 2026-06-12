package fr.bsodium.cron.ui.screens.settings.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import fr.bsodium.cron.ui.components.rememberCronHaptics
import fr.bsodium.cron.ui.theme.Spacing

/** Title/subtitle row with a 0–60 minute slider for buffer durations. */
@Composable
internal fun BufferSlider(
    label: String,
    description: String,
    value: Int,
    onChange: (Int) -> Unit,
    hapticsEnabled: Boolean = true,
) {
    val haptics = rememberCronHaptics(enabled = hapticsEnabled)
    var lastStep by remember { mutableIntStateOf(value) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "$value min",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(modifier = Modifier.height(Spacing.sm))
        Slider(
            value = value.toFloat(),
            onValueChange = { f ->
                val i = f.toInt()
                if (i != lastStep) { haptics.tick(); lastStep = i }
                onChange(i)
            },
            valueRange = 0f..60f,
            steps = 11,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
