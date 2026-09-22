package fr.bsodium.cron.ui.screens.memory

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.tooling.preview.Preview
import fr.bsodium.cron.FabRegistry
import fr.bsodium.cron.ROUTE_MEMORY
import fr.bsodium.cron.memory.MemoryEntry
import fr.bsodium.cron.ui.components.FabAction
import fr.bsodium.cron.ui.components.PageAppBar
import fr.bsodium.cron.ui.screens.memory.components.MemoryEmptyState
import fr.bsodium.cron.ui.screens.memory.components.MemoryEntryRow
import fr.bsodium.cron.ui.screens.memory.components.MemoryFullScreenComposer
import fr.bsodium.cron.ui.screens.memory.components.MemorySkeleton
import fr.bsodium.cron.ui.theme.CronPreview
import fr.bsodium.cron.ui.theme.MaterialSymbol
import fr.bsodium.cron.ui.theme.Spacing
import kotlinx.datetime.Clock
import java.util.Locale

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
    modifier: Modifier = Modifier,
    onComposerExpandedChange: (Boolean) -> Unit = {},
) {
    val entries by viewModel.entries.collectAsState()
    val isMutating by viewModel.isMutating.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    MemoryContent(
        entries = entries,
        isMutating = isMutating,
        isLoading = isLoading,
        onSend = viewModel::sendInstruction,
        onDelete = viewModel::deleteEntry,
        onRetry = viewModel::retryEntry,
        onAddAnyway = viewModel::addAnyway,
        fabRegistry = fabRegistry,
        onComposerExpandedChange = onComposerExpandedChange,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun MemoryContent(
    entries: List<MemoryEntry>,
    isMutating: Boolean,
    isLoading: Boolean,
    onSend: (String) -> Unit,
    onDelete: (Long) -> Unit,
    modifier: Modifier = Modifier,
    onRetry: (Long) -> Unit = {},
    onAddAnyway: (Long) -> Unit = {},
    fabRegistry: FabRegistry? = null,
    onComposerExpandedChange: (Boolean) -> Unit = {},
) {
    var draft by rememberSaveable { mutableStateOf("") }
    var composerExpanded by rememberSaveable { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val navInsetBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    // Publish the collapsed trigger through FabRegistry so both navigation modes share one FAB host.
    DisposableEffect(fabRegistry) {
        onDispose { fabRegistry?.clear(ROUTE_MEMORY) }
    }
    LaunchedEffect(composerExpanded) {
        onComposerExpandedChange(composerExpanded)
    }
    LaunchedEffect(composerExpanded, fabRegistry) {
        // Keep the global FAB host empty while the full-screen composer is open.
        if (!composerExpanded) {
            fabRegistry?.set(
                ROUTE_MEMORY,
                FabAction(onClick = { composerExpanded = true }, icon = MaterialSymbol.HistoryEdu, label = "Remember", filled = false),
            )
        } else {
            fabRegistry?.clear(ROUTE_MEMORY)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0),
            topBar = {
                PageAppBar(
                    title = "Memory",
                    subtitle = "Things Cron should remember about you",
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { inner ->
            val viewState = when {
                isLoading -> MemoryViewState.Loading
                entries.isEmpty() && !isMutating -> MemoryViewState.Empty
                else -> MemoryViewState.Content
            }
            Crossfade(
                targetState = viewState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        top = inner.calculateTopPadding(),
                        bottom = navInsetBottom + Spacing.navBarClearance,
                    ),
                animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                label = "memory-view-state-crossfade",
            ) { state ->
                when (state) {
                    MemoryViewState.Loading -> MemorySkeleton(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(start = Spacing.lg, end = Spacing.lg, top = Spacing.sm)
                    )
                    MemoryViewState.Empty -> MemoryEmptyState()
                    MemoryViewState.Content -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = Spacing.lg,
                            end = Spacing.lg,
                            top = Spacing.sm,
                        ),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        entries
                            .groupBy { it.category?.trim()?.takeIf(String::isNotEmpty) ?: UNCATEGORISED }
                            .toList()
                            .sortedBy { it.first.lowercase(Locale.ROOT) }
                            .forEach { (category, groupedEntries) ->
                                item(key = "section-$category") {
                                    SectionHeader(
                                        label = category,
                                        modifier = Modifier.animateItem(),
                                    )
                                }
                                items(
                                    items = groupedEntries,
                                    key = { entry -> entry.id },
                                ) { entry ->
                                    MemoryEntryRow(
                                        entry = entry,
                                        onDelete = { onDelete(entry.id) },
                                        onRetry = { onRetry(entry.id) },
                                        onAddAnyway = { onAddAnyway(entry.id) },
                                        modifier = Modifier.animateItem(),
                                    )
                                }
                            }
                    }
                }
            }
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

private const val UNCATEGORISED = "Uncategorised"

private enum class MemoryViewState { Loading, Empty, Content }

// Changing this label's padding? MemorySkeleton.kt's CategoryHeaderSkeleton copies it exactly.
@Composable
private fun SectionHeader(
    label: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = Spacing.lg, top = Spacing.lg, bottom = Spacing.xs),
    )
}

@Preview(showBackground = true, name = "Memory — with entries")
@Composable
private fun MemoryContentPreview() {
    val now = Clock.System.now()
    CronPreview {
        MemoryContent(
            entries = listOf(
                MemoryEntry(id = 1, text = "Prefers earlier wake-ups on gym days", category = "Schedule", createdAt = now, updatedAt = now),
                MemoryEntry(id = 2, text = "Commutes by bike", category = null, createdAt = now, updatedAt = now),
            ),
            isMutating = false,
            isLoading = false,
            onSend = {},
            onDelete = {},
            onRetry = {},
            onAddAnyway = {},
        )
    }
}

@Preview(showBackground = true, name = "Memory — loading")
@Composable
private fun MemoryContentLoadingPreview() {
    CronPreview {
        MemoryContent(
            entries = emptyList(),
            isMutating = false,
            isLoading = true,
            onSend = {},
            onDelete = {},
            onRetry = {},
            onAddAnyway = {},
        )
    }
}

@Preview(showBackground = true, name = "Memory — empty")
@Composable
private fun MemoryContentEmptyPreview() {
    CronPreview {
        MemoryContent(
            entries = emptyList(),
            isMutating = false,
            isLoading = false,
            onSend = {},
            onDelete = {},
            onRetry = {},
            onAddAnyway = {},
        )
    }
}

@Preview(showBackground = true, name = "Memory — mutating")
@Composable
private fun MemoryContentMutatingPreview() {
    val now = Clock.System.now()
    CronPreview {
        MemoryContent(
            entries = listOf(
                MemoryEntry(id = 1, text = "Commutes by bike", category = null, createdAt = now, updatedAt = now),
                MemoryEntry(id = 2, text = "", category = null, createdAt = now, updatedAt = now, pending = true),
            ),
            isMutating = true,
            isLoading = false,
            onSend = {},
            onDelete = {},
            onRetry = {},
            onAddAnyway = {},
        )
    }
}

@Preview(showBackground = true, name = "Memory — failure state")
@Composable
private fun MemoryContentFailurePreview() {
    val now = Clock.System.now()
    CronPreview {
        MemoryContent(
            entries = listOf(
                MemoryEntry(
                    id = 1,
                    text = "Already known",
                    category = null,
                    createdAt = now,
                    updatedAt = now,
                    failureReason = "ALREADY_EXISTS: I already know you commute by bike.",
                    instruction = "Commutes by bike"
                ),
            ),
            isMutating = false,
            isLoading = false,
            onSend = {},
            onDelete = {},
            onAddAnyway = {},
        )
    }
}
