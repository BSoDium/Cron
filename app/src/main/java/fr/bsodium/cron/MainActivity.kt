package fr.bsodium.cron

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.TopAppBarState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import fr.bsodium.cron.ui.screens.settings.LocalSettingsListState
import fr.bsodium.cron.ui.screens.settings.LocalSettingsTopAppBarState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import fr.bsodium.cron.settings.SecureKeyStore
import fr.bsodium.cron.settings.SettingsRepository
import fr.bsodium.cron.ui.components.CronFloatingNav
import fr.bsodium.cron.ui.components.CronNavigationBar
import fr.bsodium.cron.ui.components.EdgeFades
import fr.bsodium.cron.ui.components.FabAction
import fr.bsodium.cron.ui.components.PrimaryActionFab
import fr.bsodium.cron.ui.components.SplitActionFab
import fr.bsodium.cron.ui.components.rememberFabChevron
import fr.bsodium.cron.ui.screens.history.HistoryScreen
import fr.bsodium.cron.ui.screens.history.HistoryViewModel
import fr.bsodium.cron.ui.screens.home.HomeScreen
import fr.bsodium.cron.ui.screens.home.HomeViewModel
import fr.bsodium.cron.ui.screens.onboarding.OnboardingScreen
import fr.bsodium.cron.ui.screens.onboarding.OnboardingViewModel
import fr.bsodium.cron.ui.screens.settings.SETTINGS_ROOT
import fr.bsodium.cron.ui.screens.settings.settingsGraph
import fr.bsodium.cron.ui.theme.CronColors
import fr.bsodium.cron.ui.theme.CronTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

private const val COMPACT_NAV_MIN_WIDTH_DP = 360
private const val FORWARD_MS = 350
private const val TAB_OUT_MS = 90
private const val TAB_IN_MS = 160
private const val TAB_SCALE = 0.92f
private const val ROUTE_ONBOARDING = "onboarding"

/** Tab destinations, shared with [CronBottomBar] so a route rename can't silently un-highlight a tab. */
const val ROUTE_HOME = "home"
const val ROUTE_HISTORY = "history"

private val TAB_ROUTES = setOf(ROUTE_HOME, ROUTE_HISTORY, SETTINGS_ROOT)

private val forwardTween = tween<Float>(durationMillis = FORWARD_MS, easing = EaseInOutCubic)

/**
 * Tab-to-tab transitions: Material **fade-through** — the convention for unrelated top-level destinations
 * (a directional slide is the *shared-axis* pattern, reserved for hierarchical navigation). The incoming
 * fade is delayed past the outgoing fade so the two screens never sit fully opaque at once — that's what
 * removes the ghost overlap. `NavGraphBuilder` lambdas aren't composable, so this is a sanctioned `tween`
 * exception (see docs/navigation.md). Shared with `SETTINGS_ROOT` via [settingsGraph].
 */
internal val tabEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    fadeIn(tween(TAB_IN_MS, delayMillis = TAB_OUT_MS, easing = LinearOutSlowInEasing)) +
        scaleIn(
            tween(TAB_IN_MS, delayMillis = TAB_OUT_MS, easing = LinearOutSlowInEasing),
            initialScale = TAB_SCALE,
        )
}
internal val tabExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    fadeOut(tween(TAB_OUT_MS, easing = FastOutLinearInEasing)) +
        scaleOut(tween(TAB_OUT_MS, easing = FastOutLinearInEasing), targetScale = TAB_SCALE)
}

/**
 * Lets a screen publish its primary action to the [CronFloatingNav] FAB while
 * the screen is mounted. The FAB clears when the screen calls [clear] from its
 * onDispose hook, so other tabs don't inherit a stale action.
 */
class FabRegistry {
    var action by mutableStateOf<FabAction?>(null)
        private set

    fun set(action: FabAction?) {
        this.action = action
    }

    fun clear() {
        action = null
    }
}

class MainActivity : ComponentActivity() {

    /**
     * The Scaffold padding is intentionally unused: content draws edge-to-edge and each screen
     * folds the status-bar / nav insets into its own content padding.
     */
    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Suppress("UnusedMaterial3ScaffoldPaddingParameter")
    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        // System-driven bar styling — dark icons on a light page, light icons on dark.
        enableEdgeToEdge()

        val settings = SettingsRepository(this)
        // Resolve the start destination off the main thread: SecureKeyStore init (keystore/Tink) and the
        // DataStore read both block, and gating the first frame on them shows a white window. The branded
        // splash stays up until this lands, then the NavHost composes immediately.
        val startDestination = mutableStateOf<String?>(null)
        splash.setKeepOnScreenCondition { startDestination.value == null }

        setContent {
            CronTheme {
                LaunchedEffect(Unit) {
                    if (startDestination.value == null) {
                        startDestination.value = withContext(Dispatchers.IO) {
                            val done = settings.onboardingComplete.first()
                            if (done && SecureKeyStore(this@MainActivity).hasAnthropicKey()) ROUTE_HOME else ROUTE_ONBOARDING
                        }
                    }
                }
                val start = startDestination.value ?: return@CronTheme
                val navController = rememberNavController()
                val backStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = backStackEntry?.destination?.route
                val showBottomBar = currentRoute in TAB_ROUTES
                // Pages with a PageAppBar own the status-bar strip; the top edge-fade would two-tone it
                // against the bar's scrolled surfaceContainer shade, so suppress it there.
                val hasTopAppBar = currentRoute == ROUTE_HISTORY ||
                    currentRoute?.startsWith("settings") == true
                val fabRegistry = remember { FabRegistry() }
                val fabChevron = rememberFabChevron()
                val compactNavPref by settings.compactNavEnabled.collectAsState(initial = false)
                val screenWidthDp = LocalConfiguration.current.screenWidthDp
                val useCompactNav = compactNavPref && screenWidthDp >= COMPACT_NAV_MIN_WIDTH_DP
                val settingsListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
                val settingsTopAppBarState = rememberSaveable(saver = TopAppBarState.Saver) { TopAppBarState(0f, 0f, 1f) }
                val navigate: (String) -> Unit = { route ->
                    navController.navigate(route) {
                        popUpTo(ROUTE_HOME) {
                            saveState = true
                            inclusive = false
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }

                CompositionLocalProvider(
                    LocalSettingsListState provides settingsListState,
                    LocalSettingsTopAppBarState provides settingsTopAppBarState,
                ) {
                    Scaffold(
                        containerColor = CronColors.pageBackground,
                        bottomBar = {
                            AnimatedVisibility(
                                visible = showBottomBar,
                                enter = slideInVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) { it / 3 } +
                                    fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                                exit = fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec()),
                            ) {
                                if (useCompactNav) {
                                    CronFloatingNav(
                                        currentRoute = currentRoute,
                                        onNavigate = navigate,
                                        fabAction = fabRegistry.action,
                                        fabChevron = fabChevron,
                                    )
                                } else {
                                    CronNavigationBar(
                                        currentRoute = currentRoute,
                                        onNavigate = navigate,
                                    )
                                }
                            }
                        },
                        floatingActionButton = {
                            if (!useCompactNav) {
                                val action = fabRegistry.action
                                var lastShown by remember { mutableStateOf(action) }
                                if (action != null) lastShown = action
                                val fabVisible = currentRoute == ROUTE_HOME && action != null
                                val fabAlpha by animateFloatAsState(
                                    targetValue = if (fabVisible) 1f else 0f,
                                    animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
                                    label = "fab-alpha",
                                )
                                if (lastShown != null && (fabVisible || fabAlpha > 0f)) {
                                    Box(modifier = Modifier.graphicsLayer { alpha = fabAlpha }) {
                                        if (fabChevron != null) SplitActionFab(lastShown, fabChevron)
                                        else PrimaryActionFab(lastShown)
                                    }
                                }
                            }
                        },
                    ) { _ ->
                        // Edge-to-edge: each screen folds the status-bar inset into its own top padding and
                        // carries bottom padding for the nav pill; EdgeFades overlays soft top/bottom scrims.
                        Box(modifier = Modifier.fillMaxSize()) {
                            NavHost(
                                navController = navController,
                                startDestination = start,
                            ) {
                                composable(
                                    route = ROUTE_ONBOARDING,
                                    enterTransition = { fadeIn(animationSpec = forwardTween) },
                                    exitTransition = { fadeOut(animationSpec = forwardTween) },
                                ) {
                                    OnboardingScreen(
                                        viewModel = viewModel<OnboardingViewModel>(),
                                        onComplete = {
                                            navController.navigate(ROUTE_HOME) {
                                                popUpTo(ROUTE_ONBOARDING) { inclusive = true }
                                            }
                                        },
                                    )
                                }
                                composable(
                                    route = ROUTE_HOME,
                                    // Directional tab slide (onboarding → home fades, handled inside tabEnter). All
                                    // four slots use the index-directional specs so push/pop both read correctly and
                                    // nothing ever stacks at the same position.
                                    enterTransition = tabEnter,
                                    exitTransition = tabExit,
                                    popEnterTransition = tabEnter,
                                    popExitTransition = tabExit,
                                ) {
                                    HomeScreen(
                                        viewModel = viewModel<HomeViewModel>(),
                                        fabRegistry = fabRegistry,
                                        onNavigateToSettings = {
                                            navController.navigate(SETTINGS_ROOT) {
                                                popUpTo(ROUTE_HOME) {
                                                    saveState = true
                                                    inclusive = false
                                                }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        },
                                    )
                                }
                                composable(
                                    route = ROUTE_HISTORY,
                                    enterTransition = tabEnter,
                                    exitTransition = tabExit,
                                    popEnterTransition = tabEnter,
                                    popExitTransition = tabExit,
                                ) {
                                    HistoryScreen(viewModel = viewModel<HistoryViewModel>())
                                }
                                settingsGraph(navController, tabEnter = tabEnter, tabExit = tabExit)
                            }
                            EdgeFades(showTopScrim = !hasTopAppBar)
                        }
                    }
                }
            }
        }
    }
}
