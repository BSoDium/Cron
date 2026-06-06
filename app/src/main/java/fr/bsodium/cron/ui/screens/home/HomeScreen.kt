package fr.bsodium.cron.ui.screens.home

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import fr.bsodium.cron.FabRegistry
import fr.bsodium.cron.ui.components.FabAction
import fr.bsodium.cron.ui.screens.home.components.AiFailureBanner
import fr.bsodium.cron.ui.screens.home.components.GreetingHeader
import fr.bsodium.cron.ui.screens.home.components.NextAlarmCard
import fr.bsodium.cron.ui.screens.home.components.NotificationPermissionRow
import fr.bsodium.cron.ui.screens.home.components.OnboardingHint
import fr.bsodium.cron.ui.screens.home.components.SettingsChangedPill
import fr.bsodium.cron.ui.screens.home.components.StreamingHaptics
import fr.bsodium.cron.ui.screens.home.components.rememberRevealedThread
import fr.bsodium.cron.ui.theme.Spacing

// Cross-dissolve between the loading / idle / plan layouts so first-load and re-plans fade in
// instead of hard-cutting the card from one layout to another.
private const val HOME_FADE_MS = 240

/** What the home body should show — kept coarse (not the thread content) so it only crossfades on a
 *  real state change, never on each streaming update. */
private enum class HomePhase { Loading, Idle, Plan }

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

    Box(modifier = Modifier.fillMaxSize()) {
        // Gate on `initialized` so we never flash the idle layout before data resolves, then crossfade
        // straight into the right layout — no empty→thread pop / card jump on cold start.
        val homePhase = when {
            !uiState.initialized -> HomePhase.Loading
            displayThread != null -> HomePhase.Plan
            else -> HomePhase.Idle
        }
        // Keep the last shown thread so a Plan→Idle dissolve still renders it while fading out.
        var lastThread by remember { mutableStateOf<AiThreadUi?>(null) }
        LaunchedEffect(displayThread) { displayThread?.let { lastThread = it } }
        Crossfade(
            targetState = homePhase,
            animationSpec = tween(HOME_FADE_MS),
            label = "home-phase",
            modifier = Modifier.fillMaxSize(),
        ) { phase ->
            when (phase) {
                HomePhase.Loading -> Unit
                HomePhase.Idle -> HomeIdleContent(
                    uiState = uiState,
                    statusInsetTop = statusInsetTop,
                    navInsetBottom = navInsetBottom,
                    hasNotificationPermission = hasNotificationPermission,
                    onNotifEnable = onNotifEnable,
                )
                HomePhase.Plan -> (displayThread ?: lastThread)?.let { thread ->
                    HomePlanContent(
                        thread = thread,
                        uiState = uiState,
                        statusInsetTop = statusInsetTop,
                        navInsetBottom = navInsetBottom,
                        hasNotificationPermission = hasNotificationPermission,
                        onNotifEnable = onNotifEnable,
                        showPullHint = !uiState.thinkingHintSeen,
                        onFirstExpand = viewModel::markThinkingHintSeen,
                    )
                }
            }
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

/** First-run / no-plan / loading layout: greeting, the alarm card, and the onboarding hint. */
@Composable
private fun HomeIdleContent(
    uiState: HomeUiState,
    statusInsetTop: Dp,
    navInsetBottom: Dp,
    hasNotificationPermission: Boolean,
    onNotifEnable: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            // Horizontal inset is applied per-child so the card can hug wider than the rest.
            .padding(
                top = statusInsetTop + Spacing.xxl,
                bottom = navInsetBottom + Spacing.navBarClearance,
            ),
        verticalArrangement = Arrangement.spacedBy(Spacing.xl),
    ) {
        GreetingHeader(
            prefix = uiState.greetingPrefix,
            name = uiState.greetingName,
            modifier = Modifier.padding(horizontal = Spacing.xl),
        )
        Box(Modifier.fillMaxWidth().padding(horizontal = Spacing.md)) {
            NextAlarmCard(
                dateLabel = uiState.dateLabel,
                alarmTime = uiState.sessionDisplay?.alarmTime,
                sleepDurationLabel = uiState.sleepStats?.durationLabel,
                sleepSegments = uiState.sleepStats?.segments.orEmpty(),
            )
        }
        // Bias the hint toward the upper third so "Let's get started" sits near eye height
        // rather than floating in the dead-centre of the gap.
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = Spacing.xl),
            contentAlignment = BiasAlignment(0f, -0.4f),
        ) {
            OnboardingHint()
        }
        if (!hasNotificationPermission) {
            NotificationPermissionRow(
                onEnable = onNotifEnable,
                modifier = Modifier.padding(horizontal = Spacing.xl),
            )
        }
    }
}
