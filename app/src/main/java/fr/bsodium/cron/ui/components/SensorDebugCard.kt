package fr.bsodium.cron.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fr.bsodium.cron.session.model.SessionEvent

@Composable
fun SensorDebugCard(
    recentEvents: List<SessionEvent>,
    onStart: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Sensor session",
            style = MaterialTheme.typography.titleSmall,
        )
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = onStart, modifier = Modifier.weight(1f)) { Text("Start") }
            OutlinedButton(onClick = onStop, modifier = Modifier.weight(1f)) { Text("Stop") }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (recentEvents.isEmpty()) {
            Text(
                text = "No sensor events yet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(recentEvents) { event ->
                    Text(
                        text = "${event.timestamp} ${event.trigger.name} ${event.data::class.simpleName}",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}
