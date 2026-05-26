package fr.bsodium.cron

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import fr.bsodium.cron.settings.SecureKeyStore
import fr.bsodium.cron.settings.SettingsRepository
import fr.bsodium.cron.ui.screens.home.HomeScreen
import fr.bsodium.cron.ui.screens.home.HomeViewModel
import fr.bsodium.cron.ui.screens.onboarding.OnboardingScreen
import fr.bsodium.cron.ui.screens.onboarding.OnboardingViewModel
import fr.bsodium.cron.ui.screens.settings.SettingsScreen
import fr.bsodium.cron.ui.screens.settings.SettingsViewModel
import fr.bsodium.cron.ui.theme.CronTheme
import kotlinx.coroutines.flow.first

private const val FORWARD_MS = 350
private const val POP_MS = 300

private val forwardTween = tween<Float>(durationMillis = FORWARD_MS, easing = EaseInOutCubic)
private val forwardIntTween = tween<androidx.compose.ui.unit.IntOffset>(durationMillis = FORWARD_MS, easing = EaseInOutCubic)
private val popTween = tween<Float>(durationMillis = POP_MS, easing = EaseInOutCubic)
private val popIntTween = tween<androidx.compose.ui.unit.IntOffset>(durationMillis = POP_MS, easing = EaseInOutCubic)

/** Settings slides in from the right at full screen width. */
private val settingsEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    slideInHorizontally(animationSpec = forwardIntTween) { fullWidth -> fullWidth } +
        fadeIn(animationSpec = forwardTween)
}

/** Settings scales down + slides right when popped (predictive-back gesture). */
private val settingsPopExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    slideOutHorizontally(animationSpec = popIntTween) { fullWidth -> fullWidth } +
        scaleOut(animationSpec = popTween, targetScale = 0.88f) +
        fadeOut(animationSpec = popTween)
}

/** Home recedes (subtle scale-down + small left shift) when Settings opens on top. */
private val homeForwardExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    slideOutHorizontally(animationSpec = forwardIntTween) { fullWidth -> -fullWidth / 6 } +
        scaleOut(animationSpec = forwardTween, targetScale = 0.94f) +
        fadeOut(animationSpec = forwardTween)
}

/** Home slides back in from the left + scales up when Settings is popped. */
private val homePopEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    slideInHorizontally(animationSpec = popIntTween) { fullWidth -> -fullWidth / 6 } +
        scaleIn(animationSpec = popTween, initialScale = 0.94f) +
        fadeIn(animationSpec = popTween)
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

                NavHost(navController = navController, startDestination = startDestination) {
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
                        enterTransition = { fadeIn(animationSpec = forwardTween) },
                        exitTransition = homeForwardExit,
                        popEnterTransition = homePopEnter,
                        popExitTransition = { fadeOut(animationSpec = popTween) },
                    ) {
                        HomeScreen(
                            viewModel = viewModel<HomeViewModel>(),
                            onNavigateToSettings = { navController.navigate("settings") },
                        )
                    }
                    composable(
                        route = "settings",
                        enterTransition = settingsEnter,
                        exitTransition = { fadeOut(animationSpec = forwardTween) },
                        popEnterTransition = { fadeIn(animationSpec = popTween) },
                        popExitTransition = settingsPopExit,
                    ) {
                        SettingsScreen(
                            viewModel = viewModel<SettingsViewModel>(),
                            onBack = navController::navigateUp,
                        )
                    }
                }
            }
        }
    }
}
