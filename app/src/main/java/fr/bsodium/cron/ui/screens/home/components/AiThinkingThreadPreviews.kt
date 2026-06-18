package fr.bsodium.cron.ui.screens.home.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import fr.bsodium.cron.ui.screens.home.AiThreadUi
import fr.bsodium.cron.ui.screens.home.ProcessItem
import fr.bsodium.cron.ui.theme.CronTheme
import fr.bsodium.cron.ui.theme.Spacing

private val PREVIEW_THREAD = AiThreadUi(
    turnIndex = 0,
    summary = "Setting your alarm",
    process = listOf(
        ProcessItem.Reasoning(
            "Let me read the calendar for the next 24-30 hours and find the first event you must be " +
                "ready for. All-day markers like **Office** or a city set the day's working location; a " +
                "virtual `#stand-up` is a real anchor with no commute. I subtract the travel buffer and " +
                "`preparation_time` from the anchor, then nudge into a light-sleep window.",
        ),
        ProcessItem.Tool(name = "read_calendar", isComplete = true, contextLabel = "6 events"),
        ProcessItem.Tool(name = "estimate_commute_multi_mode", isComplete = true, contextLabel = "13 min"),
        ProcessItem.Tool(name = "set_alarm", isComplete = true, contextLabel = "set for 06:40"),
    ),
    response = "Set a **6:40** alarm so you make your 9:00 stand-up.\n\n" +
        "Your first anchor is at the office, about a 25 min drive. I took the commute plus 45 min of " +
        "`preparation_time` off the start, then landed on a light-sleep moment just before.",
    durationSeconds = 15,
)

@Preview(showBackground = true, name = "Thread — settled")
@Composable
private fun AiThinkingThreadPreview() {
    CronTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            AiThinkingThread(
                thread = PREVIEW_THREAD,
                modifier = Modifier.padding(Spacing.xl),
            )
        }
    }
}

@Preview(showBackground = true, name = "Disclosure — expanded")
@Composable
private fun ThinkingDisclosureExpandedPreview() {
    CronTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.xl)) {
                ThinkingDisclosure(
                    summary = PREVIEW_THREAD.summary,
                    process = PREVIEW_THREAD.process,
                    inProgress = false,
                    pending = false,
                    durationSeconds = PREVIEW_THREAD.durationSeconds,
                    isMocked = false,
                    expanded = true,
                    onToggle = {},
                )
            }
        }
    }
}

@Preview(showBackground = true, name = "Thread — running")
@Composable
private fun AiThinkingThreadRunningPreview() {
    CronTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            AiThinkingThread(
                thread = AiThreadUi(
                    turnIndex = 0,
                    summary = "Reading your calendar",
                    process = listOf(ProcessItem.Tool(name = "read_calendar", isComplete = false)),
                    response = null,
                    isStreaming = true,
                ),
                modifier = Modifier.padding(Spacing.xl),
            )
        }
    }
}
