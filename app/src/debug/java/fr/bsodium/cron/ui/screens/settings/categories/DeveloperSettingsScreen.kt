package fr.bsodium.cron.ui.screens.settings.categories

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
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
import fr.bsodium.cron.session.SessionFsm
import fr.bsodium.cron.session.SessionRepository
import fr.bsodium.cron.session.model.EventData
import fr.bsodium.cron.session.model.SessionEvent
import fr.bsodium.cron.session.model.TriggerType
import fr.bsodium.cron.ui.screens.settings.components.SettingsDetailScaffold
import fr.bsodium.cron.ui.screens.settings.components.SwitchRow
import fr.bsodium.cron.ui.theme.Spacing
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

@OptIn(ExperimentalLayoutApi::class)
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
        FsmEventInjector(context, scope)
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FsmEventInjector(
    context: android.content.Context,
    scope: kotlinx.coroutines.CoroutineScope,
) {
    val repository = remember { SessionRepository(context) }
    val fsm = remember { SessionFsm(context, repository) }

    Column {
        Text(
            text = "Inject FSM event",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = "Fire events into the active session's state machine",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            modifier = Modifier.padding(top = Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            FsmInjectButton("Sleep Onset", scope) {
                fsm.onEvent(
                    SessionEvent(
                        trigger = TriggerType.SleepOnset,
                        timestamp = Clock.System.now(),
                        data = EventData.SleepOnset(
                            screenOffSince = Clock.System.now(),
                            rearm = false,
                        ),
                    ),
                )
            }
            FsmInjectButton("Alarm Dismissed", scope) {
                fsm.onEvent(
                    SessionEvent(
                        trigger = TriggerType.AlarmDismissed,
                        timestamp = Clock.System.now(),
                        data = EventData.Empty,
                    ),
                )
            }
            FsmInjectButton("Sleep Onset (rearm)", scope) {
                fsm.onEvent(
                    SessionEvent(
                        trigger = TriggerType.SleepOnset,
                        timestamp = Clock.System.now(),
                        data = EventData.SleepOnset(
                            screenOffSince = Clock.System.now(),
                            rearm = true,
                        ),
                    ),
                )
            }
            FsmInjectButton("Out Of Bed", scope) {
                fsm.onEvent(
                    SessionEvent(
                        trigger = TriggerType.OutOfBedConfirmed,
                        timestamp = Clock.System.now(),
                        data = EventData.OutOfBedConfirmed(evidence = listOf("debug injection")),
                    ),
                )
            }
        }
    }
}

@Composable
private fun FsmInjectButton(
    label: String,
    scope: kotlinx.coroutines.CoroutineScope,
    action: suspend () -> String?,
) {
    val context = LocalContext.current
    FilledTonalButton(
        onClick = {
            scope.launch {
                val sessionId = action()
                val msg = if (sessionId != null) {
                    val repo = SessionRepository(context)
                    val session = repo.findById(sessionId)
                    "$label → ${session?.status ?: "?"}"
                } else {
                    "No active session"
                }
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }
        },
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}
