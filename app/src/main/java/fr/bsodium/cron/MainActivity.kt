package fr.bsodium.cron

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import fr.bsodium.cron.settings.SecureKeyStore
import fr.bsodium.cron.settings.SettingsRepository
import fr.bsodium.cron.ui.components.CronBottomBar
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

class MainActivity : ComponentActivity() {

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
                onboardingDone ?: return@CronTheme

                val startDestination = if (onboardingDone!! && secureStore.hasAnthropicKey()) "home" else "onboarding"
                val navController = rememberNavController()
                val backStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = backStackEntry?.destination?.route
                val showBottomBar = currentRoute in TAB_ROUTES

                Scaffold(
                    bottomBar = {
                        if (showBottomBar) {
                            CronBottomBar(
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
                            )
                        }
                    },
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = startDestination,
                        modifier = Modifier.padding(innerPadding),
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
                            HomeScreen(viewModel = viewModel<HomeViewModel>())
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
                            SettingsScreen(
                                viewModel = viewModel<SettingsViewModel>(),
                                onBack = { navController.navigate("home") { popUpTo("home") { inclusive = false } } },
                            )
                        }
                    }
                }
            }
        }
    }
}
