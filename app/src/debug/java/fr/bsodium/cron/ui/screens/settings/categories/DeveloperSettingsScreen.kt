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
import fr.bsodium.cron.debug.SleepTestPrefs
import fr.bsodium.cron.service.SleepSessionService
import fr.bsodium.cron.session.SessionFsm
import fr.bsodium.cron.session.SessionRepository
import fr.bsodium.cron.session.model.ActivityType
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
import kotlinx.datetime.TimeZone
import kotlin.time.Duration.Companion.minutes

@Composable
fun DeveloperSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { MockApiPrefs(context) }
    var mockEnabled by remember { mutableStateOf(prefs.isEnabled) }
    val sleepPrefs = remember { SleepTestPrefs(context) }
    var fastOnset by remember { mutableStateOf(sleepPrefs.fastOnset) }
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
        SwitchRow(
            title = "Fast sleep onset (10s)",
            subtitle = "Collapse onset/rearm thresholds to seconds (restart the session to apply)",
            checked = fastOnset,
            onCheckedChange = { on ->
                sleepPrefs.fastOnset = on
                fastOnset = on
            },
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Start sleep session",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = "Bootstrap a session and start the sensor monitor now",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = {
                context.startService(
                    SleepSessionService.eveningPlanIntent(context, TimeZone.currentSystemDefault().id),
                )
            }) {
                Text("Start")
            }
        }
        FsmEventInjector(context, scope)
        WorkerInjector()
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FsmEventInjector(
    context: android.content.Context,
    scope: kotlinx.coroutines.CoroutineScope,
) {
    val repo = remember { SessionRepository(context) }
    var driveFsm by remember { mutableStateOf(false) }

    // Append-only records the event; FSM mode routes through SessionFsm.onEvent so transitions,
    // the auto-plan gate (Bug 4) and the AI cooldown (Bug 3) actually run.
    val inject: suspend (SessionEvent) -> Boolean = { event ->
        if (driveFsm) {
            SessionFsm(context, repo).onEvent(event) != null
        } else {
            val session = repo.findCurrent()
            if (session == null) false else { repo.appendEvent(session.id, event); true }
        }
    }

    Column {
        Text(
            text = "Inject timeline event",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        SwitchRow(
            title = "Drive through FSM (transitions + AI)",
            subtitle = "Route injected events through SessionFsm.onEvent — exercises cooldown + auto-plan gating",
            checked = driveFsm,
            onCheckedChange = { driveFsm = it },
        )
        FlowRow(
            modifier = Modifier.padding(top = Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            InjectButton("Sleep Onset", scope) {
                inject(SessionEvent(
                    trigger = TriggerType.SleepOnset,
                    timestamp = Clock.System.now(),
                    data = EventData.SleepOnset(screenOffSince = Clock.System.now(), rearm = false),
                ))
            }
            InjectButton("Mid Sleep Activity", scope) {
                inject(SessionEvent(
                    trigger = TriggerType.MidSleepActivity,
                    timestamp = Clock.System.now(),
                    data = EventData.MidSleepActivity(
                        activityType = ActivityType.Still,
                        screenOn = false,
                        durationSeconds = 0,
                    ),
                ))
            }
            InjectButton("Alarm Dismissed", scope) {
                inject(SessionEvent(
                    trigger = TriggerType.AlarmDismissed,
                    timestamp = Clock.System.now(),
                    data = EventData.Empty,
                ))
            }
            InjectButton("Alarm Snoozed", scope) {
                inject(SessionEvent(
                    trigger = TriggerType.AlarmSnoozed,
                    timestamp = Clock.System.now(),
                    data = EventData.Empty,
                ))
            }
            InjectButton("Out Of Bed", scope) {
                inject(SessionEvent(
                    trigger = TriggerType.OutOfBedConfirmed,
                    timestamp = Clock.System.now(),
                    data = EventData.OutOfBedConfirmed(evidence = listOf("debug injection")),
                ))
            }
        }
    }
}

@Composable
private fun InjectButton(
    label: String,
    scope: kotlinx.coroutines.CoroutineScope,
    action: suspend () -> Boolean,
) {
    val context = LocalContext.current
    FilledTonalButton(
        onClick = {
            scope.launch {
                val msg = if (action()) "$label injected" else "No active session"
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }
        },
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}
