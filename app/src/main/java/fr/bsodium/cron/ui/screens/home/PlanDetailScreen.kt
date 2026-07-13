package fr.bsodium.cron.ui.screens.home

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import fr.bsodium.cron.ui.components.PageAppBar
import fr.bsodium.cron.ui.components.rememberCronHaptics
import fr.bsodium.cron.ui.screens.home.components.AiThinkingThread
import fr.bsodium.cron.ui.theme.CronColors
import fr.bsodium.cron.ui.theme.CronTheme
import fr.bsodium.cron.ui.theme.Spacing
import kotlinx.coroutines.launch

private const val PULL_THRESHOLD_FRACTION = 0.4f
private const val PULL_RUBBER_FLOOR = 0.15f
private val PULL_TRIGGER_MAX = 120.dp

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PlanDetailScreen(
    iteration: AiIterationUi?,
    hapticsEnabled: Boolean,
    onBack: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CronColors.pageBackground,
        topBar = {
            PageAppBar(
                title = iteration?.systemMessage.orEmpty(),
                scrollBehavior = scrollBehavior,
                onBack = onBack,
            )
        },
    ) { innerPadding ->
        if (iteration != null) {
            val scope = rememberCoroutineScope()
            val pullState = remember(iteration.turnIndex) { PullState() }
            val scrollState = rememberScrollState()
            val pullConnection = rememberPullConnection(
                scrollState = scrollState,
                pullState = pullState,
                hasProcess = iteration.thread.process.isNotEmpty(),
                hapticsEnabled = hapticsEnabled,
            )

            /** Read explicitly rather than guessing a flat bottom padding — a 3-button nav bar and a
             *  gesture nav bar differ enough in height that a single guessed constant cleared one but
             *  not the other, leaving the assistant shape uncleared once the thinking timeline is
             *  expanded and scrolled to the bottom. Layered on top of Scaffold's own `innerPadding`,
             *  which only reserves top/side safe-drawing insets. */
            val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .nestedScroll(pullConnection)
                    .verticalScroll(scrollState)
                    .padding(horizontal = Spacing.xl)
                    .padding(bottom = navBarBottom + Spacing.xxxxl),
            ) {
                AiThinkingThread(
                    thread = iteration.thread,
                    expanded = pullState.expanded,
                    onExpandedChange = { next ->
                        // Expand must animate reveal to full BEFORE flipping `expanded`, mirroring
                        // onPreFling below — ExpandReveal's targetPx clips straight to the full
                        // measured height once `expanded` is true, bypassing this Animatable entirely,
                        // so flipping it synchronously here made expand-by-tap snap instantly instead
                        // of animating. Collapse has no such bypass, so it can flip first as before.
                        if (next) {
                            scope.launch {
                                val full = pullState.fullPx.intValue
                                if (full > 0) pullState.reveal.animateTo(full.toFloat())
                                pullState.expanded = true
                            }
                        } else {
                            pullState.expanded = false
                            scope.launch { pullState.reveal.animateTo(0f) }
                        }
                    },
                    expandPx = { pullState.reveal.value },
                    onFullHeight = { pullState.fullPx.intValue = it },
                    expansionFraction = {
                        val full = pullState.fullPx.intValue
                        if (full > 0) (pullState.reveal.value / full).coerceIn(0f, 1f) else 0f
                    },
                )
            }
        }
    }
}

internal class PullState {
    val reveal = Animatable(0f)
    val fullPx = mutableIntStateOf(0)
    var expanded by mutableStateOf(false)
    var pastThreshold by mutableStateOf(false)
}

@Composable
private fun rememberPullConnection(
    scrollState: ScrollState,
    pullState: PullState,
    hasProcess: Boolean,
    hapticsEnabled: Boolean,
): NestedScrollConnection {
    val scope = rememberCoroutineScope()
    val triggerMaxPx = with(LocalDensity.current) { PULL_TRIGGER_MAX.toPx() }
    val hasProcessState = rememberUpdatedState(hasProcess)
    val haptics = rememberUpdatedState(rememberCronHaptics(enabled = hapticsEnabled))

    return remember(scrollState, pullState, triggerMaxPx) {
        object : NestedScrollConnection {
            fun triggerPx(): Float {
                val full = pullState.fullPx.intValue
                return if (full > 0) minOf(full * PULL_THRESHOLD_FRACTION, triggerMaxPx) else Float.MAX_VALUE
            }

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                if (available.y < 0f && pullState.reveal.value > 0f && !pullState.expanded) {
                    val next = (pullState.reveal.value + available.y).coerceAtLeast(0f)
                    val consumed = next - pullState.reveal.value
                    pullState.pastThreshold = next >= triggerPx()
                    scope.launch { pullState.reveal.snapTo(next) }
                    return Offset(0f, consumed)
                }
                val canPull = hasProcessState.value && !pullState.expanded
                if (available.y > 0f && canPull && scrollState.value == 0) {
                    val full = pullState.fullPx.intValue
                    val rubber = if (full > 0) (1f - pullState.reveal.value / full).coerceIn(PULL_RUBBER_FLOOR, 1f) else 1f
                    val next = pullState.reveal.value + available.y * rubber
                    val nowPast = next >= triggerPx()
                    if (nowPast && !pullState.pastThreshold) haptics.value.tick()
                    pullState.pastThreshold = nowPast
                    scope.launch { pullState.reveal.snapTo(next) }
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (pullState.reveal.value <= 0f) return Velocity.Zero
                val full = pullState.fullPx.intValue
                if (full > 0 && pullState.reveal.value >= triggerPx()) {
                    pullState.reveal.animateTo(full.toFloat())
                    pullState.expanded = true
                } else {
                    pullState.reveal.animateTo(0f)
                }
                return available
            }
        }
    }
}

@Preview(showBackground = true, name = "Plan detail — settled")
@Composable
private fun PlanDetailScreenPreview() {
    CronTheme {
        PlanDetailScreen(
            iteration = AiIterationUi(
                turnIndex = 0,
                timeLabel = "23:14",
                kind = RunKind.ScheduledBase,
                thread = AiThreadUi(
                    turnIndex = 0,
                    summary = "Set a 6:40 alarm",
                    process = listOf(
                        ProcessItem.Tool(name = "read_calendar", isComplete = true, contextLabel = "6 events"),
                        ProcessItem.Tool(name = "set_alarm", isComplete = true, contextLabel = "set for 06:40"),
                    ),
                    response = "Set a **6:40** alarm so you make your 9:00 stand-up.",
                    durationSeconds = 15,
                ),
                ranAtEpochMs = 0L,
            ),
            hapticsEnabled = false,
            onBack = {},
        )
    }
}
