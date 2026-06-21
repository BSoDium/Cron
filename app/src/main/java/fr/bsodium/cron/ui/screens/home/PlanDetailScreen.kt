package fr.bsodium.cron.ui.screens.home

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import fr.bsodium.cron.ui.components.rememberCronHaptics
import fr.bsodium.cron.ui.screens.home.components.AiThinkingThread
import fr.bsodium.cron.ui.theme.MaterialSymbol
import fr.bsodium.cron.ui.theme.Spacing
import fr.bsodium.cron.ui.theme.Symbol
import kotlinx.coroutines.launch

const val ROUTE_PLAN_DETAIL = "plan-detail/{turnIndex}/{sessionId}"

fun planDetailRoute(turnIndex: Int, sessionId: String) = "plan-detail/$turnIndex/$sessionId"

private const val PULL_THRESHOLD_FRACTION = 0.4f
private const val PULL_RUBBER_FLOOR = 0.15f
private val PULL_TRIGGER_MAX = 120.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanDetailScreen(
    iteration: AiIterationUi?,
    hapticsEnabled: Boolean,
    onBack: () -> Unit,
) {
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = iteration?.systemMessage.orEmpty(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Symbol(
                            symbol = MaterialSymbol.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                ),
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

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .nestedScroll(pullConnection)
                    .verticalScroll(scrollState)
                    .padding(horizontal = Spacing.xl)
                    .padding(bottom = Spacing.xxxl),
            ) {
                AiThinkingThread(
                    thread = iteration.thread,
                    expanded = pullState.expanded,
                    onExpandedChange = { next ->
                        pullState.expanded = next
                        scope.launch {
                            if (next) {
                                val full = pullState.fullPx.intValue
                                if (full > 0) pullState.reveal.animateTo(full.toFloat())
                            } else {
                                pullState.reveal.animateTo(0f)
                            }
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
