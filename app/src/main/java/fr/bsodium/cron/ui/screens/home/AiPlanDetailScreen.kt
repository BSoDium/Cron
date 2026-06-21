package fr.bsodium.cron.ui.screens.home

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import fr.bsodium.cron.ui.screens.home.components.AiThinkingThread
import fr.bsodium.cron.ui.theme.CronColors
import fr.bsodium.cron.ui.theme.CronTheme
import fr.bsodium.cron.ui.theme.MaterialSymbol
import fr.bsodium.cron.ui.theme.Radius
import fr.bsodium.cron.ui.theme.Spacing
import fr.bsodium.cron.ui.theme.Symbol
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

private const val COMMIT_MS = 300
private const val CANCEL_MS = 220
private const val CARD_MIN_SCALE = 0.90f
private val CARD_PREVIEW_SHIFT = Spacing.lg
private const val SCRIM_MAX_ALPHA = 0.5f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AiPlanDetailScreen(
    iteration: AiIterationUi,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val progress = remember { Animatable(0f) }
    var edgeLeft by remember { mutableStateOf(false) }
    var active by remember { mutableStateOf(false) }
    var committing by remember { mutableStateOf(false) }

    val density = LocalDensity.current
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val screenWidthPx = with(density) { (screenWidthDp.dp + 48.dp).toPx() }
    val previewShiftPx = with(density) { CARD_PREVIEW_SHIFT.toPx() }

    fun animatedBack() {
        if (committing) return
        committing = true
        active = true
        scope.launch {
            progress.animateTo(2f, tween(COMMIT_MS, easing = EaseOutCubic))
            onBack()
        }
    }

    PredictiveBackHandler(enabled = !committing) { events ->
        active = true
        try {
            events.collect { event ->
                edgeLeft = event.swipeEdge == BackEventCompat.EDGE_LEFT
                progress.snapTo(decelerate(event.progress))
            }
            committing = true
            progress.animateTo(2f, tween(COMMIT_MS, easing = EaseOutCubic))
            onBack()
        } catch (cancel: CancellationException) {
            scope.launch {
                progress.animateTo(0f, tween(CANCEL_MS, easing = EaseOutCubic))
                active = false
            }
            throw cancel
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (active) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val commit = (progress.value - 1f).coerceIn(0f, 1f)
                        alpha = SCRIM_MAX_ALPHA * (1f - commit)
                    }
                    .background(Color.Black),
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val p = progress.value
                    val preview = p.coerceIn(0f, 1f)
                    val commit = (p - 1f).coerceIn(0f, 1f)
                    val sign = if (edgeLeft) -1f else 1f
                    val scale = lerp(1f, CARD_MIN_SCALE, preview)
                    scaleX = scale; scaleY = scale
                    alpha = 1f - commit
                    translationX = sign * (previewShiftPx * preview + (screenWidthPx - previewShiftPx) * commit)
                    clip = true
                    shape = RoundedCornerShape(Radius.xl * preview)
                },
        ) {
            val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
            Scaffold(
                modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
                topBar = {
                    LargeTopAppBar(
                        title = { Text(iteration.systemMessage) },
                        navigationIcon = {
                            IconButton(onClick = ::animatedBack) {
                                Symbol(
                                    symbol = MaterialSymbol.ArrowBack,
                                    contentDescription = "Back",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        },
                        scrollBehavior = scrollBehavior,
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = CronColors.pageBackground,
                            scrolledContainerColor = CronColors.pageBackground,
                        ),
                    )
                },
                containerColor = CronColors.pageBackground,
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = Spacing.xl),
                ) {
                    AiThinkingThread(thread = iteration.thread)
                }
            }
        }
    }
}

private fun decelerate(raw: Float): Float {
    val x = raw.coerceIn(0f, 1f)
    return 1f - (1f - x) * (1f - x)
}

@Preview(showBackground = true)
@Composable
private fun AiPlanDetailScreenPreview() {
    val iteration = AiIterationUi(
        turnIndex = 0,
        timeLabel = "23:14",
        kind = RunKind.ScheduledBase,
        thread = AiThreadUi(
            turnIndex = 0,
            summary = "Thought for 8s",
            process = listOf(
                ProcessItem.Reasoning("Looking at your calendar for tomorrow..."),
                ProcessItem.Tool("read_calendar", isComplete = true, contextLabel = "3 events"),
            ),
            response = "Set alarm for **7:15**. Your first meeting is at 9:00, and you'll need about 45 minutes to get ready and 30 minutes for your commute.",
        ),
        ranAtEpochMs = System.currentTimeMillis(),
    )
    CronTheme {
        AiPlanDetailScreen(iteration = iteration, onBack = {})
    }
}
