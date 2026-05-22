package fr.bsodium.cron

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.lifecycle.viewmodel.compose.viewModel
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
                    composable("onboarding") {
                        OnboardingScreen(
                            viewModel = viewModel<OnboardingViewModel>(),
                            onComplete = {
                                navController.navigate("home") {
                                    popUpTo("onboarding") { inclusive = true }
                                }
                            },
                        )
                    }
                    composable("home") {
                        HomeScreen(
                            viewModel = viewModel<HomeViewModel>(),
                            onNavigateToSettings = { navController.navigate("settings") },
                        )
                    }
                    composable("settings") {
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
