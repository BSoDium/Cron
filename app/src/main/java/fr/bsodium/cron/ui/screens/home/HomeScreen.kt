package fr.bsodium.cron.ui.screens.home

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import fr.bsodium.cron.FabRegistry
import fr.bsodium.cron.ui.components.FabAction
import fr.bsodium.cron.ui.components.rememberCronHaptics
import kotlinx.coroutines.launch
import fr.bsodium.cron.ui.screens.home.components.AiFailureBanner
import fr.bsodium.cron.ui.screens.home.components.AiThinkingThread
import fr.bsodium.cron.ui.screens.home.components.GreetingHeader
import fr.bsodium.cron.ui.screens.home.components.NextAlarmCard
import fr.bsodium.cron.ui.screens.home.components.NotificationPermissionRow
import fr.bsodium.cron.ui.screens.home.components.OnboardingHint
import fr.bsodium.cron.ui.screens.home.components.SettingsChangedPill
import fr.bsodium.cron.ui.screens.home.components.StreamingHaptics
import fr.bsodium.cron.ui.screens.home.components.rememberRevealedThread
import fr.bsodium.cron.ui.theme.Spacing

// Pull-to-expand feel: the release fraction (of full height) past which it snaps open (springs back
// below), and the rubber-band floor so the reveal still creeps near full instead of fully stalling.
private const val THINKING_EXPAND_THRESHOLD = 0.4f
private const val THINKING_PULL_RUBBER_FLOOR = 0.15f
// Absolute cap on the release-to-expand pull distance, so a tall timeline (where 40% of full height
// exceeds the screen) is still openable with a short drag.
private val THINKING_EXPAND_TRIGGER_MAX = 120.dp

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    fabRegistry: FabRegistry,
    onNavigateToSettings: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    // The displayed thread is the typewriter-revealed view of the streaming thread (whole when settled).
    val displayThread = rememberRevealedThread(uiState.aiThread)
    // Subtle haptic ticks paced to the reveal animation (gated by the preference). UI-less effect.
    StreamingHaptics(thread = displayThread, enabled = uiState.hapticsEnabled)
    DisposableEffect(viewModel, fabRegistry) {
        fabRegistry.set(FabAction(onClick = viewModel::retryAiPlan, onCancel = viewModel::cancelAiPlan))
        onDispose { fabRegistry.clear() }
    }
    // Onboarding callout (rendered above EdgeFades in MainActivity): only in the loaded, no-plan, idle state.
    val showOnboardingHint = uiState.initialized && displayThread == null && !uiState.isRetrying
    LaunchedEffect(uiState.isRetrying, showOnboardingHint, fabRegistry) {
        fabRegistry.set(
            FabAction(
                onClick = viewModel::retryAiPlan,
                working = uiState.isRetrying,
                onCancel = viewModel::cancelAiPlan,
                hint = if (showOnboardingHint) "Click here to start" else null,
            )
        )
    }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasNotificationPermission by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasNotificationPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val navInsetBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val statusInsetTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val onNotifEnable = {
        context.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            }
        )
    }
    val card: @Composable () -> Unit = {
        NextAlarmCard(
            dateLabel = uiState.dateLabel,
            alarmTime = uiState.sessionDisplay?.alarmTime,
            sleepDurationLabel = uiState.sleepStats?.durationLabel,
            sleepSegments = uiState.sleepStats?.segments.orEmpty(),
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (displayThread == null) {
            // First-run / no-plan / loading: a simple centred Column. The hint + arrow render only
            // once `initialized`, so they never flash over an existing plan on cold start.
            val showOnboarding = uiState.initialized
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = Spacing.xl,
                        end = Spacing.xl,
                        top = statusInsetTop + Spacing.xxl,
                        bottom = navInsetBottom + Spacing.navBarClearance,
                    ),
                verticalArrangement = Arrangement.spacedBy(Spacing.xl),
            ) {
                GreetingHeader(
                    prefix = uiState.greetingPrefix,
                    name = uiState.greetingName,
                )
                card()
                // Bias the hint toward the upper third so "Let's get started" sits near eye height
                // rather than floating in the dead-centre of the gap.
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = BiasAlignment(0f, -0.4f),
                ) {
                    if (showOnboarding) OnboardingHint()
                }
                if (!hasNotificationPermission) NotificationPermissionRow(onEnable = onNotifEnable)
            }
            // The onboarding callout that points at the play FAB is rendered in MainActivity,
            // layered above EdgeFades (via FabAction.hint) so the bottom scrim doesn't fade it.
        } else {
            // The alarm card acts like CSS `position: sticky` (top = statusBar + gap): the list scrolls
            // under the status bar while the card sticks just below it. It's an overlay, not a
            // stickyHeader (which pins at y=0 and can't take a top offset); an "alarm-spacer" holds its flow slot.
            val listState = rememberLazyListState()
            val density = LocalDensity.current
            var cardHeightPx by remember { mutableIntStateOf(0) }

            // Pull-to-expand: at the top of the list, an overscroll-down drag unwraps the collapsed
            // thinking 1:1 with the finger. One reveal value (revealed pixels) drives the disclosure and
            // the release/tap animation, so there's no second source to dip against. Reset per turn.
            val scope = rememberCoroutineScope()
            var thinkingExpanded by rememberSaveable(displayThread.turnIndex) { mutableStateOf(false) }
            val reveal = remember(displayThread.turnIndex) { Animatable(0f) }
            val thinkingFullPx = remember(displayThread.turnIndex) { mutableIntStateOf(0) }
            val canPull = rememberUpdatedState(displayThread.process.isNotEmpty() && !thinkingExpanded)
            val pullHaptics = rememberCronHaptics()
            val expandTriggerMaxPx = with(density) { THINKING_EXPAND_TRIGGER_MAX.toPx() }
            // Tracks whether the pull is past the release-to-open threshold, so a click fires once per crossing.
            val pastThreshold = remember(displayThread.turnIndex) { mutableStateOf(false) }
            val pullConnection = remember(listState, reveal, expandTriggerMaxPx, pastThreshold) {
                object : NestedScrollConnection {
                    // Pixels of pull past which releasing snaps the block open — the same point onPreFling tests.
                    fun triggerPx(): Float {
                        val full = thinkingFullPx.intValue
                        return if (full > 0) minOf(full * THINKING_EXPAND_THRESHOLD, expandTriggerMaxPx) else Float.MAX_VALUE
                    }

                    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                        if (source != NestedScrollSource.UserInput) return Offset.Zero
                        // Dragging up while a partial peek is open unwinds it (1:1) before the list scrolls.
                        // Skip once fully expanded, or the upward scroll is eaten instead of moving the list.
                        if (available.y < 0f && reveal.value > 0f && !thinkingExpanded) {
                            val next = (reveal.value + available.y).coerceAtLeast(0f)
                            val consumed = next - reveal.value
                            pastThreshold.value = next >= triggerPx()
                            scope.launch { reveal.snapTo(next) }
                            return Offset(0f, consumed)
                        }
                        // At the top, dragging down on a collapsible, collapsed thread grows the reveal in
                        // pixels (tracks the finger), gently resisting near full so it never runs ahead.
                        if (available.y > 0f && canPull.value && !listState.canScrollBackward) {
                            val full = thinkingFullPx.intValue
                            val rubber = if (full > 0) {
                                (1f - reveal.value / full).coerceIn(THINKING_PULL_RUBBER_FLOOR, 1f)
                            } else {
                                1f
                            }
                            val next = reveal.value + available.y * rubber
                            val nowPast = next >= triggerPx()
                            if (nowPast && !pastThreshold.value) pullHaptics.tick() // click as the pull reaches the open point
                            pastThreshold.value = nowPast
                            scope.launch { reveal.snapTo(next) }
                            return Offset(0f, available.y)
                        }
                        return Offset.Zero
                    }

                    override suspend fun onPreFling(available: Velocity): Velocity {
                        if (reveal.value <= 0f) return Velocity.Zero
                        val full = thinkingFullPx.intValue
                        if (full > 0 && reveal.value >= triggerPx()) {
                            reveal.animateTo(full.toFloat())
                            thinkingExpanded = true
                        } else {
                            reveal.animateTo(0f)
                        }
                        return available
                    }
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().nestedScroll(pullConnection),
                contentPadding = PaddingValues(
                    start = Spacing.xl,
                    end = Spacing.xl,
                    top = statusInsetTop + Spacing.xxl,
                    bottom = navInsetBottom + Spacing.navBarClearance,
                ),
                verticalArrangement = Arrangement.spacedBy(Spacing.xl),
            ) {
                item(key = "greeting") {
                    GreetingHeader(
                        prefix = uiState.greetingPrefix,
                        name = uiState.greetingName,
                    )
                }
                item(key = "alarm-spacer") {
                    Spacer(Modifier.height(with(density) { cardHeightPx.toDp() }))
                }
                item(key = "thread") {
                    AiThinkingThread(
                        thread = displayThread,
                        isRunning = uiState.isRetrying,
                        expanded = thinkingExpanded,
                        // Tap animates the same reveal value (snap to full before a close so it eases
                        // down from the natural height the expanded state was showing).
                        onExpandedChange = { open ->
                            scope.launch {
                                if (open) {
                                    reveal.animateTo(thinkingFullPx.intValue.toFloat())
                                    thinkingExpanded = true
                                } else {
                                    thinkingExpanded = false
                                    reveal.snapTo(thinkingFullPx.intValue.toFloat())
                                    reveal.animateTo(0f)
                                }
                            }
                        },
                        expandPx = reveal.value,
                        onFullHeight = { if (it != thinkingFullPx.intValue) thinkingFullPx.intValue = it },
                        expansionFraction = if (thinkingExpanded) 1f
                            else (reveal.value / thinkingFullPx.intValue.toFloat().coerceAtLeast(1f))
                                .coerceIn(0f, 1f),
                    )
                }
                if (!hasNotificationPermission) {
                    item(key = "notif-permission") {
                        NotificationPermissionRow(onEnable = onNotifEnable)
                    }
                }
            }
            StickyAlarm(
                listState = listState,
                safeTop = statusInsetTop + Spacing.sm,
                cardHeightPx = cardHeightPx,
                onHeightChanged = { cardHeightPx = it },
                card = card,
            )
        }

        // Floating callouts stack just above the nav: a turn-failure banner on top of the
        // "settings changed" pill, so the two never overlap when both are visible.
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(
                    start = Spacing.lg,
                    end = Spacing.lg,
                    bottom = navInsetBottom + Spacing.navBarClearance,
                ),
        ) {
            // Hold the last failure so it animates out cleanly after aiFailure clears on dismiss.
            var lastFailure by remember { mutableStateOf<AiTurnFailure?>(null) }
            LaunchedEffect(uiState.aiFailure) { uiState.aiFailure?.let { lastFailure = it } }
            AnimatedVisibility(
                visible = uiState.aiFailure != null,
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { it / 2 },
            ) {
                lastFailure?.let { failure ->
                    AiFailureBanner(
                        failure = failure,
                        onOpenSettings = onNavigateToSettings,
                        onDismiss = viewModel::dismissAiFailure,
                        modifier = Modifier.padding(bottom = Spacing.sm),
                    )
                }
            }
            AnimatedVisibility(
                visible = uiState.settingsChangedSincePlan,
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { it / 2 },
            ) {
                SettingsChangedPill(
                    onRewrite = {
                        viewModel.retryAiPlan()
                        viewModel.dismissSettingsReminder()
                    },
                    onDismiss = viewModel::dismissSettingsReminder,
                )
            }
        }
    }
}

private data class StickyAlarmState(val top: Int, val gradientAlpha: Float)

/**
 * CSS-`sticky`-with-top-offset alarm card, rendered as an overlay over the list. It follows the
 * flow position of the "alarm-spacer" item, then holds at [safeTop] (just below the status bar).
 * When stuck, a full-width background→transparent gradient fades in behind it, dissolving content
 * that slides under the card (and the full-bleed pill's edges) into the page background.
 */
@Composable
private fun BoxScope.StickyAlarm(
    listState: LazyListState,
    safeTop: Dp,
    cardHeightPx: Int,
    onHeightChanged: (Int) -> Unit,
    card: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val safeTopPx = with(density) { safeTop.roundToPx() }
    val fadePx = with(density) { Spacing.xxl.toPx() }
    val state by remember(safeTopPx, fadePx) {
        derivedStateOf {
            val info = listState.layoutInfo
            // On-screen top = item offset − viewportStartOffset (= −beforeContentPadding). Null once
            // the spacer scrolls off the top → fully stuck (handled explicitly to avoid int overflow).
            val screenTop = info.visibleItemsInfo.firstOrNull { it.key == "alarm-spacer" }
                ?.let { it.offset - info.viewportStartOffset }
            if (screenTop == null) {
                StickyAlarmState(top = safeTopPx, gradientAlpha = 1f)
            } else {
                StickyAlarmState(
                    top = maxOf(safeTopPx, screenTop),
                    gradientAlpha = ((safeTopPx - screenTop) / fadePx).coerceIn(0f, 1f),
                )
            }
        }
    }
    val background = MaterialTheme.colorScheme.background
    // Solid bg down to the card bottom, then a short fade below so content dissolves as it scrolls under.
    val cardBottomPx = safeTopPx + cardHeightPx
    val belowFadePx = with(density) { Spacing.xxxl.toPx() }
    val totalPx = cardBottomPx + belowFadePx
    val solidStop = if (totalPx > 0f) (cardBottomPx / totalPx).coerceIn(0f, 1f) else 1f
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(with(density) { totalPx.toDp() })
            .graphicsLayer { alpha = state.gradientAlpha }
            .background(
                Brush.verticalGradient(
                    0f to background,
                    solidStop to background,
                    1f to Color.Transparent,
                ),
            ),
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { translationY = state.top.toFloat() }
            .padding(horizontal = Spacing.xl)
            .onSizeChanged { if (it.height != cardHeightPx) onHeightChanged(it.height) },
    ) { card() }
}
