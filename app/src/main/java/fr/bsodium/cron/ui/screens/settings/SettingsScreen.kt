package fr.bsodium.cron.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.datetime.LocalTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            SectionHeader("Schedule")

            TimeSettingRow(
                label = "Evening trigger",
                description = "When Cron plans tonight's alarm",
                time = state.eveningTrigger,
                onHourChanged = { h ->
                    viewModel.setEveningTrigger(LocalTime(h, state.eveningTrigger.minute))
                },
                onMinuteChanged = { m ->
                    viewModel.setEveningTrigger(LocalTime(state.eveningTrigger.hour, m))
                },
            )

            TimeSettingRow(
                label = "Hard latest",
                description = "Absolute latest the alarm can fire",
                time = state.hardLatest,
                onHourChanged = { h ->
                    viewModel.setHardLatest(LocalTime(h, state.hardLatest.minute))
                },
                onMinuteChanged = { m ->
                    viewModel.setHardLatest(LocalTime(state.hardLatest.hour, m))
                },
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            SectionHeader("Free days")

            TimeSettingRow(
                label = "Wake window start",
                description = "Earliest to wake on days with no events",
                time = state.freeDayWakeStart,
                onHourChanged = { h ->
                    viewModel.setFreeDayWakeWindow(
                        LocalTime(h, state.freeDayWakeStart.minute),
                        state.freeDayWakeEnd,
                    )
                },
                onMinuteChanged = { m ->
                    viewModel.setFreeDayWakeWindow(
                        LocalTime(state.freeDayWakeStart.hour, m),
                        state.freeDayWakeEnd,
                    )
                },
            )

            TimeSettingRow(
                label = "Wake window end",
                description = "Latest to wake on days with no events",
                time = state.freeDayWakeEnd,
                onHourChanged = { h ->
                    viewModel.setFreeDayWakeWindow(
                        state.freeDayWakeStart,
                        LocalTime(h, state.freeDayWakeEnd.minute),
                    )
                },
                onMinuteChanged = { m ->
                    viewModel.setFreeDayWakeWindow(
                        state.freeDayWakeStart,
                        LocalTime(state.freeDayWakeEnd.hour, m),
                    )
                },
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            SectionHeader("Commute")

            Text(
                text = "Buffer before first event",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                text = "${state.commuteBufferMinutes} minutes",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Slider(
                value = state.commuteBufferMinutes.toFloat(),
                onValueChange = { viewModel.setCommuteBuffer(it.toInt()) },
                valueRange = 0f..60f,
                steps = 11,
                modifier = Modifier.fillMaxWidth(),
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            SectionHeader("Account")

            if (state.hasApiKey) {
                Text(
                    text = "Anthropic API key: stored",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                OutlinedButton(
                    onClick = viewModel::clearApiKey,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Clear API key")
                }
            } else {
                Text(
                    text = "No API key stored",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
    )
}

@Composable
private fun TimeSettingRow(
    label: String,
    description: String,
    time: LocalTime,
    onHourChanged: (Int) -> Unit,
    onMinuteChanged: (Int) -> Unit,
) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodyLarge)
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "%02d:%02d".format(time.hour, time.minute),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text("Hour", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Slider(
            value = time.hour.toFloat(),
            onValueChange = { onHourChanged(it.toInt()) },
            valueRange = 0f..23f,
            steps = 22,
            modifier = Modifier.fillMaxWidth(),
        )
        Text("Minute", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Slider(
            value = time.minute.toFloat(),
            onValueChange = { onMinuteChanged((it / 5).toInt() * 5) },
            valueRange = 0f..55f,
            steps = 10,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
