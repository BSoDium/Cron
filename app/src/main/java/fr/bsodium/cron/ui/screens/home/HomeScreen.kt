package fr.bsodium.cron.ui.screens.home

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toIntSize
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import fr.bsodium.cron.FabRegistry
import fr.bsodium.cron.session.model.ActionType
import fr.bsodium.cron.session.model.SessionStatus
import fr.bsodium.cron.ui.components.FabAction
import fr.bsodium.cron.ui.components.PredictiveBackCard
import fr.bsodium.cron.ui.theme.MaterialSymbol
import fr.bsodium.cron.ui.screens.home.components.AiFailureBanner
import fr.bsodium.cron.ui.screens.home.components.AlarmTiming
import fr.bsodium.cron.ui.screens.home.components.HomeGreetingRow
import fr.bsodium.cron.ui.screens.home.components.NextAlarmCard
import fr.bsodium.cron.ui.screens.home.components.NextPlanHint
import fr.bsodium.cron.ui.screens.home.components.NotificationPermissionRow
import fr.bsodium.cron.ui.screens.home.components.OnboardingHint
import fr.bsodium.cron.ui.screens.home.components.SettingsChangedPill
import fr.bsodium.cron.ui.screens.home.components.StreamingHaptics
import fr.bsodium.cron.ui.screens.home.components.rememberAlarmTiming
import fr.bsodium.cron.ui.screens.settings.components.TimePickerDialog
import fr.bsodium.cron.ui.screens.home.components.rememberRevealedThread
import fr.bsodium.cron.ui.theme.CronTheme
import fr.bsodium.cron.ui.theme.Spacing
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

private data object HomeRoot
private data class PlanDetailKey(val turnIndex: Int, val sessionId: String)

private const val FORWARD_MS = 350

private const val EMPTY_STATE_DATE_LABEL = "No alarm set"

/** What the home body should show — kept coarse (not the thread content) so it only crossfades on a
 *  real state change, never on each streaming update. */
private enum class HomePhase { Loading, Idle, Plan }

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    fabRegistry: FabRegistry,
    onNavigateToSettings: () -> Unit,
    onNavigateToScheduleSettings: () -> Unit = onNavigateToSettings,
    onNavigateToHistory: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()
    // At most one iteration streams at a time (always the latest). Typewriter-reveal that sub-thread and
    // splice it back so the rest of the plan renders settled.
    val streamingThread = uiState.aiPlan?.iterations?.lastOrNull { it.thread.isStreaming }?.thread
    val revealed = rememberRevealedThread(streamingThread)
    val displayPlan = uiState.aiPlan?.withStreamingReplaced(revealed)
    // Once the alarm has passed (out of date) or been dismissed (session Complete), the plan is spent:
    // Home rests on the onset card + "next plan" line rather than the now-stale thread. Ticks live via
    // rememberAlarmTiming, so it flips to resting the minute the alarm time passes.
    val timing = rememberAlarmTiming(uiState.sessionDisplay?.alarmTime, uiState.sessionDisplay?.sessionDate)
    val resting = uiState.sessionDisplay?.status == SessionStatus.Complete || timing is AlarmTiming.Past
    // Subtle haptic ticks paced to the reveal animation (gated by the preference). UI-less effect.
    StreamingHaptics(thread = revealed, enabled = uiState.hapticsEnabled)
    val isFirstRun = displayPlan == null
    val fabLabel = if (isFirstRun) "Start planning" else "Re-plan"
    val fabSplitLabel = if (isFirstRun) "Run plan" else "Re-plan"
    val fabIcon = if (isFirstRun) MaterialSymbol.RocketLaunch else MaterialSymbol.Update
    DisposableEffect(viewModel, fabRegistry) {
        fabRegistry.set(FabAction(onClick = viewModel::retryAiPlan, onCancel = viewModel::cancelAiPlan, label = fabLabel, splitLabel = fabSplitLabel, icon = fabIcon))
        onDispose { fabRegistry.clear() }
    }
    LaunchedEffect(uiState.isRetrying, fabLabel, fabSplitLabel, fabIcon, fabRegistry) {
        fabRegistry.set(
            FabAction(
                onClick = viewModel::retryAiPlan,
                working = uiState.isRetrying,
                onCancel = viewModel::cancelAiPlan,
                label = fabLabel,
                splitLabel = fabSplitLabel,
                icon = fabIcon,
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

    var showTimePicker by remember { mutableStateOf(false) }
    val alarmEditable = uiState.autoAlarmsEnabled
        && uiState.sessionDisplay?.alarmTime != null
        && timing is AlarmTiming.Upcoming
    val onAlarmTimeClick: (() -> Unit)? = if (alarmEditable) {{ showTimePicker = true }} else null

    val navInsetBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val statusInsetTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val onNotifEnable = {
        context.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            }
        )
    }

    val backStack: SnapshotStateList<Any> = remember { listOf<Any>(HomeRoot).toMutableStateList() }
    val snapshotLayer = rememberGraphicsLayer()

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        transitionSpec = {
            slideInHorizontally { it } + fadeIn() togetherWith
                slideOutHorizontally { -it / 4 } + fadeOut()
        },
        popTransitionSpec = {
            EnterTransition.None togetherWith ExitTransition.None
        },
        predictivePopTransitionSpec = {
            EnterTransition.None togetherWith ExitTransition.None
        },
        entryProvider = entryProvider {
            entry<HomeRoot> {
                HomeRootContent(
                    uiState = uiState,
                    displayPlan = displayPlan,
                    resting = resting,
                    statusInsetTop = statusInsetTop,
                    navInsetBottom = navInsetBottom,
                    hasNotificationPermission = hasNotificationPermission,
                    onNotifEnable = onNotifEnable,
                    onAutoAlarmsChange = viewModel::setAutoAlarmsEnabled,
                    onAlarmTimeClick = onAlarmTimeClick,
                    onOpenAiRun = { turn, session -> backStack.add(PlanDetailKey(turn, session)) },
                    onNavigateToHistory = onNavigateToHistory,
                    onNavigateToSettings = onNavigateToSettings,
                    onNavigateToScheduleSettings = onNavigateToScheduleSettings,
                    viewModel = viewModel,
                    showTimePicker = showTimePicker,
                    onShowTimePicker = { showTimePicker = it },
                    snapshotLayer = snapshotLayer,
                )
            }
            entry<PlanDetailKey> { key ->
                PredictiveBackCard(
                    onBack = { backStack.removeLastOrNull() },
                    parentContent = {
                        Canvas(Modifier.fillMaxSize()) {
                            drawLayer(snapshotLayer)
                        }
                    },
                ) { animatedBack ->
                    PlanDetailScreen(
                        iteration = uiState.aiPlan?.iterations?.find { it.turnIndex == key.turnIndex },
                        hapticsEnabled = uiState.hapticsEnabled,
                        onBack = animatedBack,
                    )
                }
            }
        },
    )
}

@Suppress("AnimationPreviewNotRequired")
@Composable
private fun HomeRootContent(
    uiState: HomeUiState,
    displayPlan: AiPlanUi?,
    resting: Boolean,
    statusInsetTop: Dp,
    navInsetBottom: Dp,
    hasNotificationPermission: Boolean,
    onNotifEnable: () -> Unit,
    onAutoAlarmsChange: (Boolean) -> Unit,
    onAlarmTimeClick: (() -> Unit)?,
    onOpenAiRun: (turnIndex: Int, sessionId: String) -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToScheduleSettings: () -> Unit,
    viewModel: HomeViewModel,
    showTimePicker: Boolean,
    onShowTimePicker: (Boolean) -> Unit,
    snapshotLayer: GraphicsLayer? = null,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (snapshotLayer != null) {
                    Modifier.drawWithContent {
                        snapshotLayer.record(size = size.toIntSize()) {
                            this@drawWithContent.drawContent()
                        }
                        drawLayer(snapshotLayer)
                    }
                } else {
                    Modifier
                },
            ),
    ) {
        var lastPlan by remember { mutableStateOf<AiPlanUi?>(null) }
        LaunchedEffect(displayPlan) { displayPlan?.let { lastPlan = it } }
        val homePhase = when {
            !uiState.initialized -> HomePhase.Loading
            displayPlan != null && !resting -> HomePhase.Plan
            lastPlan != null && uiState.isRetrying && !resting -> HomePhase.Plan
            else -> HomePhase.Idle
        }
        Crossfade(
            targetState = homePhase,
            animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
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
                    onAutoAlarmsChange = onAutoAlarmsChange,
                )
                HomePhase.Plan -> HomePlanContent(
                    uiState = uiState.let { state ->
                        val plan = displayPlan ?: lastPlan
                        if (plan != null) state.copy(aiPlan = plan) else state
                    },
                    statusInsetTop = statusInsetTop,
                    navInsetBottom = navInsetBottom,
                    hasNotificationPermission = hasNotificationPermission,
                    onNotifEnable = onNotifEnable,
                    onAutoAlarmsChange = onAutoAlarmsChange,
                    onAlarmTimeClick = onAlarmTimeClick,
                    onOpenAiRun = onOpenAiRun,
                    onNavigateToHistory = onNavigateToHistory,
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(
                    start = Spacing.lg,
                    end = Spacing.lg,
                    bottom = navInsetBottom + Spacing.navBarClearance,
                ),
        ) {
            var lastFailure by remember { mutableStateOf<AiTurnFailure?>(null) }
            LaunchedEffect(uiState.aiFailure) { uiState.aiFailure?.let { lastFailure = it } }
            AnimatedVisibility(
                visible = uiState.aiFailure != null,
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { it / 2 },
                label = "failure-banner",
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
                label = "settings-changed-pill",
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

        if (showTimePicker) {
            TimePickerDialog(
                initial = uiState.sessionDisplay?.alarmTime ?: LocalTime(7, 0),
                onDismiss = { onShowTimePicker(false) },
                onConfirm = { newTime ->
                    viewModel.updateAlarmTime(newTime)
                    onShowTimePicker(false)
                },
                hardLatest = uiState.sessionDisplay?.hardLatest,
                onEditLimit = {
                    onShowTimePicker(false)
                    onNavigateToScheduleSettings()
                },
            )
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
    onAutoAlarmsChange: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            // Horizontal inset is applied per-child so the card can hug wider than the rest.
            .padding(
                top = statusInsetTop + Spacing.xxl,
                bottom = navInsetBottom + Spacing.navBarClearance,
            ),
        // Tight gap from the greeting down to the card; the card→hint area is a weighted box below.
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        HomeGreetingRow(
            prefix = uiState.greetingPrefix,
            name = uiState.greetingName,
            autoAlarmsEnabled = uiState.autoAlarmsEnabled,
            onAutoAlarmsChange = onAutoAlarmsChange,
            modifier = Modifier.padding(horizontal = Spacing.xl),
            hapticsEnabled = uiState.hapticsEnabled,
        )
        Box(Modifier.fillMaxWidth().padding(horizontal = Spacing.md)) {
            // No active alarm in this state — render the blank onset card; "what's next" lives below it.
            NextAlarmCard(
                dateLabel = EMPTY_STATE_DATE_LABEL,
                alarmTime = null,
                sessionDate = null,
                sleepDurationLabel = null,
                sleepSegments = emptyList(),
            )
        }
        // Bias the hint toward the upper third so it sits near eye height rather than dead-centre.
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = Spacing.xl),
            contentAlignment = BiasAlignment(0f, -0.4f),
        ) {
            // First run (no session ever) → the onboarding invite; otherwise the between-sessions rest state.
            if (uiState.sessionDisplay == null) {
                OnboardingHint()
            } else {
                NextPlanHint(
                    autoAlarmsEnabled = uiState.autoAlarmsEnabled,
                    eveningTriggerTime = uiState.eveningTriggerTime,
                )
            }
        }
        if (!hasNotificationPermission) {
            NotificationPermissionRow(
                onEnable = onNotifEnable,
                modifier = Modifier.padding(horizontal = Spacing.xl),
            )
        }
    }
}

@Preview(showBackground = true, name = "Home — resting (no plan yet)")
@Composable
private fun HomeRestingPreview() {
    CronTheme {
        HomeIdleContent(
            uiState = HomeUiState(
                initialized = true,
                greetingPrefix = "Good evening",
                greetingName = "Elliot",
                // A completed (dismissed) session → the resting NextPlanHint branch, not first-run onboarding.
                sessionDisplay = SessionDisplayState(
                    status = SessionStatus.Complete,
                    action = ActionType.DoNothing,
                    alarmTime = null,
                    reason = "",
                    sessionDate = LocalDate(2026, 6, 8),
                    snoozeCount = 0,
                ),
                autoAlarmsEnabled = true,
                eveningTriggerTime = LocalTime(20, 0),
            ),
            statusInsetTop = 0.dp,
            navInsetBottom = 0.dp,
            hasNotificationPermission = true,
            onNotifEnable = {},
            onAutoAlarmsChange = {},
        )
    }
}
