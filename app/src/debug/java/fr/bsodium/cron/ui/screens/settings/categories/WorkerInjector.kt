package fr.bsodium.cron.ui.screens.settings.categories

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import fr.bsodium.cron.ui.theme.Spacing
import fr.bsodium.cron.worker.CalendarChangeWorker
import fr.bsodium.cron.worker.HealthConnectPollWorker

/**
 * DEBUG-ONLY. Runs the background workers on demand so their Auto-plan gate (Bug 4) can be exercised
 * without waiting for a real calendar change or the 15-min Health Connect poll. Toggle Auto-plan and
 * watch logcat (`-s CalendarChangeWorker HealthConnectPollWorker`): OFF → "Auto-plan disabled —
 * skipping"; ON → the worker proceeds.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WorkerInjector() {
    Column {
        Text(
            text = "Run background workers",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = "Trigger the gated workers now (toggle Auto-plan to see the gate in logcat)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            modifier = Modifier.padding(top = Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            RunWorkerButton("Calendar worker") {
                WorkManager.getInstance(it).enqueue(
                    OneTimeWorkRequestBuilder<CalendarChangeWorker>().build(),
                )
            }
            RunWorkerButton("HC poll worker") {
                WorkManager.getInstance(it).enqueue(
                    OneTimeWorkRequestBuilder<HealthConnectPollWorker>().build(),
                )
            }
        }
    }
}

@Composable
private fun RunWorkerButton(label: String, onRun: (android.content.Context) -> Unit) {
    val context = LocalContext.current
    FilledTonalButton(onClick = {
        onRun(context)
        Toast.makeText(context, "$label enqueued", Toast.LENGTH_SHORT).show()
    }) {
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}
