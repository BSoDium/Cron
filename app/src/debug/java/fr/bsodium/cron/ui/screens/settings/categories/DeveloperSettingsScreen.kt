package fr.bsodium.cron.ui.screens.settings.categories

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import fr.bsodium.cron.debug.MockApiPrefs
import fr.bsodium.cron.session.SessionRepository
import fr.bsodium.cron.session.model.EventData
import fr.bsodium.cron.session.model.SessionEvent
import fr.bsodium.cron.session.model.SignalConfidence
import fr.bsodium.cron.session.model.SleepStage
import fr.bsodium.cron.session.model.TriggerType
import fr.bsodium.cron.ui.screens.settings.components.SettingsDetailScaffold
import fr.bsodium.cron.ui.screens.settings.components.SwitchRow
import fr.bsodium.cron.ui.theme.Spacing
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlin.time.Duration.Companion.minutes

@Composable
fun DeveloperSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { MockApiPrefs(context) }
    var mockEnabled by remember { mutableStateOf(prefs.isEnabled) }
    var showClearDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    SettingsDetailScaffold(title = "Developer", onBack = onBack) {
        SwitchRow(
            title = "Mock API responses",
            subtitle = "Allow switching between mock and real API responses",
            checked = mockEnabled,
            onCheckedChange = { enabled ->
                prefs.isEnabled = enabled
                mockEnabled = enabled
            },
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Clear all runs",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = "Delete every session and its AI messages",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = { showClearDialog = true }) {
                Text(
                    text = "Clear",
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Inject sleep data",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = "Insert a mock 7.5h sleep cycle into the current session",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = {
                scope.launch {
                    val repo = SessionRepository(context)
                    val session = repo.findCurrent() ?: return@launch
                    val now = Clock.System.now()
                    val totalMinutes = 460
                    val start = now - totalMinutes.minutes
                    val stages = listOf(
                        SleepStage.Awake to 12,
                        SleepStage.Light to 78,
                        SleepStage.Deep to 95,
                        SleepStage.Rem to 45,
                        SleepStage.Light to 80,
                        SleepStage.Deep to 65,
                        SleepStage.Rem to 50,
                        SleepStage.Light to 35,
                    )
                    var cursor = start
                    for ((stage, mins) in stages) {
                        val end = cursor + mins.minutes
                        repo.appendEvent(
                            session.id,
                            SessionEvent(
                                trigger = TriggerType.HcStageUpdate,
                                timestamp = cursor,
                                data = EventData.HcStageUpdate(
                                    stage = stage,
                                    source = "debug",
                                    confidence = SignalConfidence.High,
                                    recordStart = cursor,
                                    recordEnd = end,
                                ),
                            ),
                        )
                        cursor = end
                    }
                }
            }) {
                Text(text = "Inject")
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Delete all sessions?") },
            text = { Text("This removes every run, including AI messages and events. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    showClearDialog = false
                    scope.launch { SessionRepository(context).clearAll() }
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}
