package fr.bsodium.cron.ui.screens.settings

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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

private const val PUSH_MS = 240
private const val POP_MS = 300

private typealias EnterSpec = AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition
private typealias ExitSpec = AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition

/**
 * Drill-down + predictive-back transitions for the Settings graph. `NavGraphBuilder` lambdas are not
 * composable so they can't read `MaterialTheme.motionScheme` — a sanctioned `tween` exception (see
 * docs/expressive.md § Sanctioned exceptions). The pop pair is what Navigation Compose *seeks* during a
 * predictive-back gesture, producing the Google-Settings scale-into-card peek; see docs/navigation.md.
 */
private val pushEnter: EnterSpec = {
    slideInHorizontally(tween(PUSH_MS, easing = EaseOutCubic)) { it } + fadeIn(tween(PUSH_MS / 2))
}
private val pushExit: ExitSpec = {
    slideOutHorizontally(tween(PUSH_MS, easing = EaseOutCubic)) { -it / 4 } +
        fadeOut(tween(PUSH_MS), targetAlpha = 0.65f)
}
private val popExit: ExitSpec = {
    scaleOut(tween(POP_MS, easing = EaseOutCubic), targetScale = 0.90f) +
        slideOutHorizontally(tween(POP_MS, easing = EaseOutCubic)) { it / 3 } +
        fadeOut(tween(POP_MS))
}
private val popEnter: EnterSpec = {
    scaleIn(tween(POP_MS, easing = EaseOutCubic), initialScale = 0.92f) +
        slideInHorizontally(tween(POP_MS, easing = EaseOutCubic)) { -it / 6 } +
        fadeIn(tween(POP_MS), initialAlpha = 0.55f)
}

private fun NavBackStackEntry.isSettingsChild() =
    destination.parent?.route == SETTINGS_GRAPH && destination.route != SETTINGS_ROOT

/**
 * Nested Settings graph: a category landing plus a sub-screen per category. Every destination shares
 * one [SettingsViewModel] scoped to the graph entry, so persisted values are consistent and the
 * sub-screens never flash defaults. Sub-screen routes sit outside the tab set, so the nav pill hides
 * on drill-down (see MainActivity's TAB_ROUTES).
 */
fun NavGraphBuilder.settingsGraph(navController: NavController) {
    navigation(route = SETTINGS_GRAPH, startDestination = SETTINGS_ROOT) {
        composable(
            SETTINGS_ROOT,
            // Entered via a tab switch → instant. Exits via a drill-down (push the child) or a tab switch
            // back to Home/History (instant). Re-entered on a child pop → the scale-up-from-behind peek.
            enterTransition = { EnterTransition.None },
            exitTransition = { if (targetState.isSettingsChild()) pushExit() else ExitTransition.None },
            popEnterTransition = popEnter,
            popExitTransition = { ExitTransition.None },
        ) {
            SettingsScreen(onOpenCategory = { route -> navController.navigate(route) })
        }
        settingsDetail(SETTINGS_SCHEDULE) { entry ->
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
        settingsDetail(SETTINGS_FREE_DAYS) { entry ->
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
        settingsDetail(SETTINGS_BUFFERS) { entry ->
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
        settingsDetail(SETTINGS_COMMUTE) { entry ->
            val vm = entry.settingsViewModel(navController)
            val state by vm.uiState.collectAsState()
            CommuteSettingsScreen(
                allowedModes = state.allowedCommuteModes,
                onAllowedModes = vm::setAllowedCommuteModes,
                onBack = { navController.popBackStack() },
            )
        }
        settingsDetail(SETTINGS_ASSISTANT) { entry ->
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
        settingsDetail(SETTINGS_RELIABILITY) {
            ReliabilitySettingsScreen(onBack = { navController.popBackStack() })
        }
        settingsDetail(SETTINGS_ACCOUNT) { entry ->
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
        settingsDetail(SETTINGS_ABOUT) {
            AboutSettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}

/**
 * A Settings sub-screen: pushes in from the right and is popped back with the predictive-back
 * scale-into-card. Sub-screens are never re-entered via a pop, so [popEnter] doesn't apply here.
 */
private fun NavGraphBuilder.settingsDetail(
    route: String,
    content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit,
) = composable(
    route = route,
    enterTransition = pushEnter,
    exitTransition = pushExit,
    popEnterTransition = { EnterTransition.None },
    popExitTransition = popExit,
    content = content,
)

/** The [SettingsViewModel] scoped to the whole settings graph, shared across landing + sub-screens. */
@Composable
private fun NavBackStackEntry.settingsViewModel(nav: NavController): SettingsViewModel {
    val parent = remember(this) { nav.getBackStackEntry(SETTINGS_GRAPH) }
    return viewModel(parent)
}
