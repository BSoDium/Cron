package fr.bsodium.cron.ui.screens.memory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.tooling.preview.Preview
import fr.bsodium.cron.FabRegistry
import fr.bsodium.cron.memory.MemoryEntry
import fr.bsodium.cron.ui.components.FabAction
import fr.bsodium.cron.ui.components.PageAppBar
import fr.bsodium.cron.ui.screens.memory.components.MemoryComposerFab
import fr.bsodium.cron.ui.screens.memory.components.MemoryEntryRow
import fr.bsodium.cron.ui.screens.memory.components.MemoryFullScreenComposer
import fr.bsodium.cron.ui.theme.CronTheme
import fr.bsodium.cron.ui.theme.MaterialSymbol
import fr.bsodium.cron.ui.theme.Spacing
import kotlinx.datetime.Clock

/**
 * The Memory tab: a list of durable assistant memory entries (ground truth on screen), laid out
 * like the Settings root (collapsing [PageAppBar] + edge-to-edge list). Tapping the FAB opens
 * [MemoryFullScreenComposer], a full-screen text input, not a chat. Sending triggers a background
 * mutation turn ([MemoryViewModel.sendInstruction]) where the assistant decides what to add/edit;
 * entries are never edited in place. Swiping a row left deletes it directly (no assistant round trip).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MemoryScreen(
    viewModel: MemoryViewModel,
    fabRegistry: FabRegistry,
    useCompactNav: Boolean,
    modifier: Modifier = Modifier,
    onComposerExpandedChange: (Boolean) -> Unit = {},
) {
    val entries by viewModel.entries.collectAsState()
    val isMutating by viewModel.isMutating.collectAsState()
    MemoryContent(
        entries = entries,
        isMutating = isMutating,
        onSend = viewModel::sendInstruction,
        onDelete = viewModel::deleteEntry,
        fabRegistry = fabRegistry,
        useCompactNav = useCompactNav,
        onComposerExpandedChange = onComposerExpandedChange,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun MemoryContent(
    entries: List<MemoryEntry>,
    isMutating: Boolean,
    onSend: (String) -> Unit,
    onDelete: (Long) -> Unit,
    fabRegistry: FabRegistry? = null,
    useCompactNav: Boolean = false,
    onComposerExpandedChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var draft by rememberSaveable { mutableStateOf("") }
    var composerExpanded by rememberSaveable { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val navInsetBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    // Compact nav: the collapsed trigger lives in CronFloatingNav's own row instead of floating
    // here (same pill-shifts-left mechanism Home's FAB already uses) — publish/withdraw a
    // FabAction as the composer opens and closes, mirroring HomeScreen's own fabRegistry usage.
    val fabOwner = remember { Any() }
    DisposableEffect(fabRegistry) {
        onDispose { fabRegistry?.clear(fabOwner) }
    }
    LaunchedEffect(composerExpanded) {
        onComposerExpandedChange(composerExpanded)
    }
    LaunchedEffect(composerExpanded, useCompactNav, fabRegistry) {
        if (useCompactNav && !composerExpanded) {
            fabRegistry?.set(
                fabOwner,
                FabAction(onClick = { composerExpanded = true }, icon = MaterialSymbol.HistoryEdu, label = "Remember", filled = false),
            )
        } else {
            fabRegistry?.clear(fabOwner)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0),
            topBar = { PageAppBar(title = "Memory", scrollBehavior = scrollBehavior) },
        ) { inner ->
            if (entries.isEmpty()) {
                Text(
                    text = "No memories yet. Tell Cron something to remember.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            start = Spacing.lg,
                            end = Spacing.lg,
                            top = inner.calculateTopPadding() + Spacing.sm,
                        ),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = Spacing.lg,
                        end = Spacing.lg,
                        top = inner.calculateTopPadding() + Spacing.sm,
                        bottom = navInsetBottom + Spacing.navBarClearance,
                    ),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    items(entries, key = { it.id }) { entry ->
                        MemoryEntryRow(entry = entry, onDelete = { onDelete(entry.id) })
                    }
                    if (isMutating) {
                        item(key = "pending") {
                            Text(
                                text = "Updating memory…",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = Spacing.sm, horizontal = Spacing.xs),
                            )
                        }
                    }
                }
            }
        }

        if (!useCompactNav) {
            MemoryComposerFab(
                visible = !composerExpanded,
                onClick = { composerExpanded = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = navInsetBottom + Spacing.navBarClearance),
            )
        }

        MemoryFullScreenComposer(
            visible = composerExpanded,
            value = draft,
            onValueChange = { draft = it },
            onSend = {
                onSend(draft)
                composerExpanded = false
            },
            onDismiss = { composerExpanded = false },
            enabled = !isMutating,
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
            onDelete = {},
        )
    }
}

@Preview(showBackground = true, name = "Memory — empty")
@Composable
private fun MemoryContentEmptyPreview() {
    CronTheme {
        MemoryContent(entries = emptyList(), isMutating = false, onSend = {}, onDelete = {})
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
            onDelete = {},
        )
    }
}
