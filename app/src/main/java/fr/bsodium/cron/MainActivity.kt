package fr.bsodium.cron

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import fr.bsodium.cron.settings.SecureKeyStore
import fr.bsodium.cron.settings.SettingsRepository
import fr.bsodium.cron.ui.components.CronFloatingNav
import fr.bsodium.cron.ui.components.EdgeFades
import fr.bsodium.cron.ui.components.FabAction
import fr.bsodium.cron.ui.components.OnboardingTooltip
import fr.bsodium.cron.ui.screens.history.HistoryScreen
import fr.bsodium.cron.ui.screens.history.HistoryViewModel
import fr.bsodium.cron.ui.screens.home.HomeScreen
import fr.bsodium.cron.ui.screens.home.HomeViewModel
import fr.bsodium.cron.ui.screens.onboarding.OnboardingScreen
import fr.bsodium.cron.ui.screens.onboarding.OnboardingViewModel
import fr.bsodium.cron.ui.screens.settings.SettingsScreen
import fr.bsodium.cron.ui.screens.settings.SettingsViewModel
import fr.bsodium.cron.ui.theme.CronTheme
import kotlinx.coroutines.flow.first

private const val FORWARD_MS = 350
private const val TAB_MS = 220

private val TAB_ROUTES = setOf("home", "history", "settings")

private val forwardTween = tween<Float>(durationMillis = FORWARD_MS, easing = EaseInOutCubic)
private val tabTween = tween<Float>(durationMillis = TAB_MS, easing = EaseInOutCubic)

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
    @Suppress("UnusedMaterial3ScaffoldPaddingParameter")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // System-driven bar styling — dark icons on a light page, light icons on dark.
        enableEdgeToEdge()

        val settings = SettingsRepository(this)
        val secureStore = SecureKeyStore(this)

        setContent {
            CronTheme {
                val onboardingDone: Boolean? by produceState<Boolean?>(initialValue = null) {
                    value = settings.onboardingComplete.first()
                }
                val done = onboardingDone ?: return@CronTheme

                val startDestination = if (done && secureStore.hasAnthropicKey()) "home" else "onboarding"
                val navController = rememberNavController()
                val backStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = backStackEntry?.destination?.route
                val showBottomBar = currentRoute in TAB_ROUTES
                val fabRegistry = remember { FabRegistry() }

                Scaffold(
                    bottomBar = {
                        if (showBottomBar) {
                            CronFloatingNav(
                                currentRoute = currentRoute,
                                onNavigate = { route ->
                                    navController.navigate(route) {
                                        popUpTo("home") {
                                            saveState = true
                                            inclusive = false
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                fabAction = fabRegistry.action,
                            )
                        }
                    },
                ) { _ ->
                    // Edge-to-edge: each screen folds the status-bar inset into its own top padding and
                    // carries bottom padding for the nav pill; EdgeFades overlays soft top/bottom scrims.
                    Box(modifier = Modifier.fillMaxSize()) {
                    NavHost(
                        navController = navController,
                        startDestination = startDestination,
                    ) {
                        composable(
                            route = "onboarding",
                            enterTransition = { fadeIn(animationSpec = forwardTween) },
                            exitTransition = { fadeOut(animationSpec = forwardTween) },
                        ) {
                            OnboardingScreen(
                                viewModel = viewModel<OnboardingViewModel>(),
                                onComplete = {
                                    navController.navigate("home") {
                                        popUpTo("onboarding") { inclusive = true }
                                    }
                                },
                            )
                        }
                        composable(
                            route = "home",
                            enterTransition = { fadeIn(animationSpec = tabTween) },
                            exitTransition = { fadeOut(animationSpec = tabTween) },
                        ) {
                            HomeScreen(
                                viewModel = viewModel<HomeViewModel>(),
                                fabRegistry = fabRegistry,
                            )
                        }
                        composable(
                            route = "history",
                            enterTransition = { fadeIn(animationSpec = tabTween) },
                            exitTransition = { fadeOut(animationSpec = tabTween) },
                        ) {
                            HistoryScreen(viewModel = viewModel<HistoryViewModel>())
                        }
                        composable(
                            route = "settings",
                            enterTransition = { fadeIn(animationSpec = tabTween) },
                            exitTransition = { fadeOut(animationSpec = tabTween) },
                        ) {
                            SettingsScreen(viewModel = viewModel<SettingsViewModel>())
                        }
                    }
                        EdgeFades()
                        // Onboarding callout for the play FAB — drawn AFTER EdgeFades so the
                        // bottom scrim doesn't fade it out; HomeScreen requests it via FabAction.hint.
                        if (currentRoute == "home") {
                            val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                            fabRegistry.action?.hint?.let { OnboardingTooltip(navBottom = navBottom, text = it) }
                        }
                    }
                }
            }
        }
    }
}
