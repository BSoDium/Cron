package fr.bsodium.cron.ui.screens.memory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import fr.bsodium.cron.memory.MemoryEntry
import fr.bsodium.cron.ui.screens.memory.components.MemoryComposer
import fr.bsodium.cron.ui.screens.memory.components.MemoryEntryRow
import fr.bsodium.cron.ui.theme.CronTheme
import fr.bsodium.cron.ui.theme.CronTypography
import fr.bsodium.cron.ui.theme.Spacing
import kotlinx.datetime.Clock

/**
 * The Memory tab: a list of durable assistant memory entries (ground truth on screen) with a
 * bottom message-style input. Typing an instruction and sending it does NOT start a chat — it
 * triggers a background mutation turn ([MemoryViewModel.sendInstruction]) where the assistant
 * decides what to add/edit/delete; entries are never edited directly from this screen.
 */
@Composable
fun MemoryScreen(viewModel: MemoryViewModel, modifier: Modifier = Modifier) {
    val entries by viewModel.entries.collectAsState()
    val isMutating by viewModel.isMutating.collectAsState()
    MemoryContent(
        entries = entries,
        isMutating = isMutating,
        onSend = viewModel::sendInstruction,
        modifier = modifier,
    )
}

@Composable
private fun MemoryContent(
    entries: List<MemoryEntry>,
    isMutating: Boolean,
    onSend: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var draft by rememberSaveable { mutableStateOf("") }
    val statusInsetTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navInsetBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Column(modifier = modifier.fillMaxSize()) {
        Text(
            text = "Memory",
            style = CronTypography.pageTitle,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = statusInsetTop + Spacing.lg, start = Spacing.lg, end = Spacing.lg, bottom = Spacing.sm),
        )
        Box(modifier = Modifier.weight(1f)) {
            if (entries.isEmpty()) {
                Text(
                    text = "No memories yet — tell me something to remember.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = Spacing.xxl),
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    items(entries, key = { it.id }) { entry -> MemoryEntryRow(entry) }
                    if (isMutating) {
                        item(key = "pending") {
                            Text(
                                text = "Updating memory…",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = Spacing.sm),
                            )
                        }
                    }
                }
            }
        }
        MemoryComposer(
            value = draft,
            onValueChange = { draft = it },
            onSend = {
                onSend(draft)
                draft = ""
            },
            enabled = !isMutating,
            modifier = Modifier
                .padding(horizontal = Spacing.lg)
                .padding(top = Spacing.sm, bottom = navInsetBottom + Spacing.navBarClearance),
        )
    }
}

@Preview(showBackground = true, name = "Memory — with entries")
@Composable
private fun MemoryContentPreview() {
    val now = Clock.System.now()
    CronTheme {
        MemoryContent(
            entries = listOf(
                MemoryEntry(id = 1, text = "Prefers earlier wake-ups on gym days", category = "schedule", createdAt = now, updatedAt = now),
                MemoryEntry(id = 2, text = "Commutes by bike", category = null, createdAt = now, updatedAt = now),
            ),
            isMutating = false,
            onSend = {},
        )
    }
}

@Preview(showBackground = true, name = "Memory — empty")
@Composable
private fun MemoryContentEmptyPreview() {
    CronTheme {
        MemoryContent(entries = emptyList(), isMutating = false, onSend = {})
    }
}

@Preview(showBackground = true, name = "Memory — mutating")
@Composable
private fun MemoryContentMutatingPreview() {
    val now = Clock.System.now()
    CronTheme {
        MemoryContent(
            entries = listOf(MemoryEntry(id = 1, text = "Commutes by bike", category = null, createdAt = now, updatedAt = now)),
            isMutating = true,
            onSend = {},
        )
    }
}
