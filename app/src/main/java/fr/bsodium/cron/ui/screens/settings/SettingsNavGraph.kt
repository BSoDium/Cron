package fr.bsodium.cron.ui.screens.settings

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import fr.bsodium.cron.BuildConfig
import fr.bsodium.cron.ui.screens.settings.categories.AboutSettingsScreen
import fr.bsodium.cron.ui.screens.settings.categories.AccountSettingsScreen
import fr.bsodium.cron.ui.screens.settings.categories.AppSettingsScreen
import fr.bsodium.cron.ui.screens.settings.categories.AssistantSettingsScreen
import fr.bsodium.cron.ui.screens.settings.categories.BuffersSettingsScreen
import fr.bsodium.cron.ui.screens.settings.categories.CalendarSettingsScreen
import fr.bsodium.cron.ui.screens.settings.categories.CommuteSettingsScreen
import fr.bsodium.cron.ui.screens.settings.categories.DeveloperSettingsScreen
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
const val SETTINGS_APP = "settings/app"
const val SETTINGS_ABOUT = "settings/about"
const val SETTINGS_CALENDAR = "settings/calendar"
const val SETTINGS_DEVELOPER = "settings/developer"

private const val PUSH_MS = 240

private typealias EnterSpec = AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition
private typealias ExitSpec = AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition

/**
 * Forward drill-down transitions for the Settings graph: the deeper screen pushes in from the right and
 * covers the parent, which parallaxes left and dims. `NavGraphBuilder` lambdas are not composable so they
 * can't read `MaterialTheme.motionScheme` — a sanctioned `tween` exception (see docs/expressive.md §
 * Sanctioned exceptions). The *back* visual is not here: it's owned by [PredictiveBackCard] inside
 * [SettingsDetailScaffold] (true two-phase predictive back), so every pop transition below is `None` and
 * the NavHost swap happens invisibly under the card animation. See docs/navigation.md.
 */
private val pushEnter: EnterSpec = {
    slideInHorizontally(tween(PUSH_MS, easing = EaseOutCubic)) { it } + fadeIn(tween(PUSH_MS / 2))
}
private val pushExit: ExitSpec = {
    slideOutHorizontally(tween(PUSH_MS, easing = EaseOutCubic)) { -it / 4 } +
        fadeOut(tween(PUSH_MS), targetAlpha = 0.65f)
}

private fun NavBackStackEntry.isSettingsChild() =
    destination.parent?.route == SETTINGS_GRAPH && destination.route != SETTINGS_ROOT

/**
 * Nested Settings graph: a category landing plus a sub-screen per category. Every destination shares
 * one [SettingsViewModel] scoped to the graph entry, so persisted values are consistent and the
 * sub-screens never flash defaults. Sub-screen routes sit outside the tab set, so the nav pill hides
 * on drill-down (see MainActivity's TAB_ROUTES).
 */
fun NavGraphBuilder.settingsGraph(
    navController: NavController,
    tabEnter: EnterSpec,
    tabExit: ExitSpec,
) {
    navigation(route = SETTINGS_GRAPH, startDestination = SETTINGS_ROOT) {
        composable(
            SETTINGS_ROOT,
            // A tab destination: tab switch IN uses the fade-through (enter); tab switch AWAY uses it
            // (popExit). Drilling into a child is the forward push (pushExit). On a child pop, the child's
            // PredictiveBackCard already animates the root coming forward, so popEnter MUST be None — a
            // transition here would re-scale the real root on top of the card's copy (a ghostly double
            // entrance). See docs/navigation.md.
            enterTransition = tabEnter,
            exitTransition = { if (targetState.isSettingsChild()) pushExit() else tabExit() },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = tabExit,
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
                hapticsEnabled = state.hapticsEnabled,
            )
        }
        settingsDetail(SETTINGS_COMMUTE) { entry ->
            val vm = entry.settingsViewModel(navController)
            val state by vm.uiState.collectAsState()
            CommuteSettingsScreen(
                allowedModes = state.allowedCommuteModes,
                onAllowedModes = vm::setAllowedCommuteModes,
                onBack = { navController.popBackStack() },
                hapticsEnabled = state.hapticsEnabled,
            )
        }
        settingsDetail(SETTINGS_CALENDAR) { entry ->
            val vm = entry.settingsViewModel(navController)
            val state by vm.uiState.collectAsState()
            CalendarSettingsScreen(
                allowedStatuses = state.allowedRsvpStatuses,
                onAllowedStatuses = vm::setAllowedRsvpStatuses,
                onBack = { navController.popBackStack() },
                hapticsEnabled = state.hapticsEnabled,
            )
        }
        settingsDetail(SETTINGS_ASSISTANT) { entry ->
            val vm = entry.settingsViewModel(navController)
            val state by vm.uiState.collectAsState()
            AssistantSettingsScreen(
                userInstructions = state.userInstructions,
                dailyTokenLimit = state.dailyTokenLimit,
                tokensUsedToday = state.tokensUsedToday,
                onUserInstructions = vm::setUserInstructions,
                onDailyTokenLimit = vm::setDailyTokenLimit,
                onRefreshUsage = vm::refreshUsage,
                onBack = { navController.popBackStack() },
            )
        }
        settingsDetail(SETTINGS_APP) { entry ->
            val vm = entry.settingsViewModel(navController)
            val state by vm.uiState.collectAsState()
            AppSettingsScreen(
                hapticsEnabled = state.hapticsEnabled,
                onHapticsEnabled = vm::setHapticsEnabled,
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
        if (BuildConfig.DEBUG) {
            settingsDetail(SETTINGS_DEVELOPER) {
                DeveloperSettingsScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}

/**
 * A Settings sub-screen: pushes in from the right on the way down. The back/pop visual is owned by
 * [PredictiveBackCard] (the two-phase scale-into-card), so both pop transitions are `None` — the NavHost
 * swap is invisible under the card animation.
 */
private fun NavGraphBuilder.settingsDetail(
    route: String,
    content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit,
) = composable(
    route = route,
    enterTransition = pushEnter,
    exitTransition = pushExit,
    popEnterTransition = { EnterTransition.None },
    popExitTransition = { ExitTransition.None },
    content = content,
)

/** The [SettingsViewModel] scoped to the whole settings graph, shared across landing + sub-screens. */
@Composable
private fun NavBackStackEntry.settingsViewModel(nav: NavController): SettingsViewModel {
    val parent = remember(this) { nav.getBackStackEntry(SETTINGS_GRAPH) }
    return viewModel(parent)
}
