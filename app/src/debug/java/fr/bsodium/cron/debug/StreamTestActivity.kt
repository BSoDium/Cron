package fr.bsodium.cron.debug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import fr.bsodium.cron.ui.screens.home.AiThreadUi
import fr.bsodium.cron.ui.screens.home.components.AiThinkingThread
import fr.bsodium.cron.ui.screens.home.components.StreamingHaptics
import fr.bsodium.cron.ui.theme.CronTheme
import fr.bsodium.cron.ui.theme.Spacing

/**
 * DEBUG-ONLY launcher (second app icon "Cron Stream Test" in debug builds). Replays a scripted
 * streaming turn through the real components so token rendering, the trailing fade, the
 * narration-vs-answer classification, and the haptics can be felt on a device with zero API spend.
 */
class StreamTestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CronTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    StreamTestScreen()
                }
            }
        }
    }
}

@Composable
private fun StreamTestScreen() {
    var thread by remember { mutableStateOf<AiThreadUi?>(null) }
    var runKey by remember { mutableIntStateOf(0) }

    // Real haptics path — reads StreamingTurnStore, which the simulator drives.
    StreamingHaptics(enabled = true)
    LaunchedEffect(runKey) { StreamingSimulator.run { thread = it } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Button(onClick = { runKey++ }) { Text("Replay stream") }
        thread?.let { AiThinkingThread(it, isRunning = true) }
    }
}
