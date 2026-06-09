package fr.bsodium.cron.ui.screens.settings

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import fr.bsodium.cron.ui.screens.settings.categories.AboutSettingsScreen
import fr.bsodium.cron.ui.screens.settings.categories.AccountSettingsScreen
import fr.bsodium.cron.ui.screens.settings.categories.AssistantSettingsScreen
import fr.bsodium.cron.ui.screens.settings.categories.BuffersSettingsScreen
import fr.bsodium.cron.ui.screens.settings.categories.CommuteSettingsScreen
import fr.bsodium.cron.ui.screens.settings.categories.FreeDaysSettingsScreen
import fr.bsodium.cron.ui.screens.settings.categories.ReliabilitySettingsScreen
import fr.bsodium.cron.ui.screens.settings.categories.ScheduleSettingsScreen

const val SETTINGS_GRAPH = "settings"
const val SETTINGS_ROOT = "settings/root"
const val SETTINGS_SCHEDULE = "settings/schedule"
const val SETTINGS_FREE_DAYS = "settings/freeDays"
const val SETTINGS_BUFFERS = "settings/buffers"
const val SETTINGS_COMMUTE = "settings/commute"
const val SETTINGS_ASSISTANT = "settings/assistant"
const val SETTINGS_RELIABILITY = "settings/reliability"
const val SETTINGS_ACCOUNT = "settings/account"
const val SETTINGS_ABOUT = "settings/about"

// NavGraphBuilder transition lambdas are not composable, so they cannot read MaterialTheme.motionScheme —
// a sanctioned exception: see docs/expressive.md § Sanctioned exceptions.
private val settingsTween = tween<Float>(durationMillis = 220, easing = EaseInOutCubic)
private val settingsEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition =
    { fadeIn(settingsTween) }
private val settingsExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition =
    { fadeOut(settingsTween) }

/**
 * Nested Settings graph: a category landing plus a sub-screen per category. Every destination shares
 * one [SettingsViewModel] scoped to the graph entry, so persisted values are consistent and the
 * sub-screens never flash defaults. Sub-screen routes sit outside the tab set, so the nav pill hides
 * on drill-down (see MainActivity's TAB_ROUTES).
 */
fun NavGraphBuilder.settingsGraph(navController: NavController) {
    navigation(route = SETTINGS_GRAPH, startDestination = SETTINGS_ROOT) {
        composable(SETTINGS_ROOT, enterTransition = settingsEnter, exitTransition = settingsExit) {
            SettingsScreen(onOpenCategory = { route -> navController.navigate(route) })
        }
        composable(SETTINGS_SCHEDULE, enterTransition = settingsEnter, exitTransition = settingsExit) { entry ->
            val vm = entry.settingsViewModel(navController)
            val state by vm.uiState.collectAsState()
            ScheduleSettingsScreen(
                eveningTrigger = state.eveningTrigger,
                hardLatest = state.hardLatest,
                onEveningTrigger = vm::setEveningTrigger,
                onHardLatest = vm::setHardLatest,
                onBack = { navController.popBackStack() },
            )
        }
        composable(SETTINGS_FREE_DAYS, enterTransition = settingsEnter, exitTransition = settingsExit) { entry ->
            val vm = entry.settingsViewModel(navController)
            val state by vm.uiState.collectAsState()
            FreeDaysSettingsScreen(
                wakeStart = state.freeDayWakeStart,
                wakeEnd = state.freeDayWakeEnd,
                onWakeStart = { vm.setFreeDayWakeWindow(it, state.freeDayWakeEnd) },
                onWakeEnd = { vm.setFreeDayWakeWindow(state.freeDayWakeStart, it) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(SETTINGS_BUFFERS, enterTransition = settingsEnter, exitTransition = settingsExit) { entry ->
            val vm = entry.settingsViewModel(navController)
            val state by vm.uiState.collectAsState()
            BuffersSettingsScreen(
                commuteBufferMinutes = state.commuteBufferMinutes,
                preparationBufferMinutes = state.preparationBufferMinutes,
                onCommuteBuffer = vm::setCommuteBuffer,
                onPreparationBuffer = vm::setPreparationBuffer,
                onBack = { navController.popBackStack() },
            )
        }
        composable(SETTINGS_COMMUTE, enterTransition = settingsEnter, exitTransition = settingsExit) { entry ->
            val vm = entry.settingsViewModel(navController)
            val state by vm.uiState.collectAsState()
            CommuteSettingsScreen(
                allowedModes = state.allowedCommuteModes,
                onAllowedModes = vm::setAllowedCommuteModes,
                onBack = { navController.popBackStack() },
            )
        }
        composable(SETTINGS_ASSISTANT, enterTransition = settingsEnter, exitTransition = settingsExit) { entry ->
            val vm = entry.settingsViewModel(navController)
            val state by vm.uiState.collectAsState()
            AssistantSettingsScreen(
                userInstructions = state.userInstructions,
                dailyTokenLimit = state.dailyTokenLimit,
                tokensUsedToday = state.tokensUsedToday,
                hapticsEnabled = state.hapticsEnabled,
                onUserInstructions = vm::setUserInstructions,
                onDailyTokenLimit = vm::setDailyTokenLimit,
                onHapticsEnabled = vm::setHapticsEnabled,
                onRefreshUsage = vm::refreshUsage,
                onBack = { navController.popBackStack() },
            )
        }
        composable(SETTINGS_RELIABILITY, enterTransition = settingsEnter, exitTransition = settingsExit) {
            ReliabilitySettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(SETTINGS_ACCOUNT, enterTransition = settingsEnter, exitTransition = settingsExit) { entry ->
            val vm = entry.settingsViewModel(navController)
            val state by vm.uiState.collectAsState()
            AccountSettingsScreen(
                displayName = state.displayName,
                hasApiKey = state.hasApiKey,
                onDisplayName = vm::setDisplayName,
                onClearApiKey = vm::clearApiKey,
                onBack = { navController.popBackStack() },
            )
        }
        composable(SETTINGS_ABOUT, enterTransition = settingsEnter, exitTransition = settingsExit) {
            AboutSettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}

/** The [SettingsViewModel] scoped to the whole settings graph, shared across landing + sub-screens. */
@Composable
private fun NavBackStackEntry.settingsViewModel(nav: NavController): SettingsViewModel {
    val parent = remember(this) { nav.getBackStackEntry(SETTINGS_GRAPH) }
    return viewModel(parent)
}
