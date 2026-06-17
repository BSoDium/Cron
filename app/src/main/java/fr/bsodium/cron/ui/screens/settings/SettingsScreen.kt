package fr.bsodium.cron.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.tooling.preview.Preview
import fr.bsodium.cron.BuildConfig
import fr.bsodium.cron.ui.components.PageAppBar
import fr.bsodium.cron.ui.screens.settings.components.SettingsCategoryRow
import fr.bsodium.cron.ui.theme.CronTheme
import fr.bsodium.cron.ui.theme.MaterialSymbol
import fr.bsodium.cron.ui.theme.Radius
import fr.bsodium.cron.ui.theme.Spacing

private data class SettingsCategory(
    val route: String,
    val icon: MaterialSymbol,
    val title: String,
    val subtitle: String,
)

private data class SettingsSection(
    val label: String,
    val categories: List<SettingsCategory>,
)

/** Account sits on its own solo card above the named sections — the most-reached destination, surfaced first. */
private val ACCOUNT_CATEGORY =
    SettingsCategory(SETTINGS_ACCOUNT, MaterialSymbol.Person, "Account", "Display name and API key")

/** Categories grouped into labeled, connected-card sections: timing · assistant + system · app. */
private val SETTINGS_SECTIONS: List<SettingsSection> = buildList {
    add(SettingsSection(
        "TIMING",
        listOf(
            SettingsCategory(SETTINGS_SCHEDULE, MaterialSymbol.Schedule, "Schedule", "When Cron plans tonight's alarm"),
            SettingsCategory(SETTINGS_FREE_DAYS, MaterialSymbol.Weekend, "Free days", "Wake window when you have no events"),
            SettingsCategory(SETTINGS_BUFFERS, MaterialSymbol.Timer, "Buffers", "Travel and preparation time"),
            SettingsCategory(SETTINGS_COMMUTE, MaterialSymbol.DirectionsCar, "Commute", "How the planner estimates travel"),
            SettingsCategory(SETTINGS_CALENDAR, MaterialSymbol.CalendarMonth, "Calendar", "Which events the planner sees"),
        ),
    ))
    add(SettingsSection(
        "ASSISTANT",
        listOf(
            SettingsCategory(SETTINGS_ASSISTANT, MaterialSymbol.AutoAwesome, "Assistant", "Instructions and token budget"),
            SettingsCategory(SETTINGS_RELIABILITY, MaterialSymbol.Shield, "Reliability", "Permissions that keep alarms on time"),
        ),
    ))
    add(SettingsSection(
        "APP",
        listOf(
            SettingsCategory(SETTINGS_APP, MaterialSymbol.Settings, "Preferences", "Haptic feedback"),
            SettingsCategory(SETTINGS_ABOUT, MaterialSymbol.Info, "About", "Credits and attributions"),
        ),
    ))
    if (BuildConfig.DEBUG) {
        add(SettingsSection(
            "DEVELOPER",
            listOf(
                SettingsCategory(SETTINGS_DEVELOPER, MaterialSymbol.Build, "Developer", "Mock mode and debug tools"),
            ),
        ))
    }
}

// Connected-card group: large radius on a group's outer corners, a barely-rounded seam where
// same-group cards meet, a tight gap within a group. Between sections the header carries the gap.
/** Scroll state for the settings root list, hoisted to MainActivity so PredictiveBackCard can snapshot it. */
val LocalSettingsListState = compositionLocalOf { LazyListState() }

/** App-bar collapse state for the settings root, hoisted so the predictive back preview reflects the real bar height. */
val LocalSettingsTopAppBarState = compositionLocalOf { TopAppBarState(0f, 0f, 1f) }

private val CARD_GAP = Spacing.xs
private val GROUP_OUTER_RADIUS = Radius.xl
private val GROUP_INNER_RADIUS = Radius.sm

/** Settings landing: tappable categories as labeled connected-card sections that drill into sub-screens. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(
    onOpenCategory: (String) -> Unit,
    listState: LazyListState = LocalSettingsListState.current,
    topAppBarState: TopAppBarState = LocalSettingsTopAppBarState.current,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(state = topAppBarState)
    val navBottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0),
        topBar = { PageAppBar(title = "Settings", scrollBehavior = scrollBehavior) },
    ) { inner ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Spacing.xl,
                end = Spacing.xl,
                top = inner.calculateTopPadding() + Spacing.md,
                bottom = navBottomInset + Spacing.navBarClearance,
            ),
        ) {
            item(key = "account") {
                SettingsCategoryRow(
                    icon = ACCOUNT_CATEGORY.icon,
                    title = ACCOUNT_CATEGORY.title,
                    subtitle = ACCOUNT_CATEGORY.subtitle,
                    shape = RoundedCornerShape(GROUP_OUTER_RADIUS),
                    onClick = { onOpenCategory(ACCOUNT_CATEGORY.route) },
                )
            }
            items(SETTINGS_SECTIONS, key = { it.label }) { section ->
                SectionHeader(section.label)
                Column(verticalArrangement = Arrangement.spacedBy(CARD_GAP)) {
                    section.categories.forEachIndexed { index, category ->
                        SettingsCategoryRow(
                            icon = category.icon,
                            title = category.title,
                            subtitle = category.subtitle,
                            shape = groupItemShape(
                                isFirst = index == 0,
                                isLast = index == section.categories.lastIndex,
                            ),
                            onClick = { onOpenCategory(category.route) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = Spacing.lg, top = Spacing.lg, bottom = Spacing.sm),
    )
}

/** Rounds a card's outer (group-edge) corners large and its seams with neighbours small. */
private fun groupItemShape(isFirst: Boolean, isLast: Boolean): RoundedCornerShape {
    val top = if (isFirst) GROUP_OUTER_RADIUS else GROUP_INNER_RADIUS
    val bottom = if (isLast) GROUP_OUTER_RADIUS else GROUP_INNER_RADIUS
    return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
}

@Preview(showBackground = true, widthDp = 480, heightDp = 300, fontScale = 1.0f)
@Composable
private fun SettingsScreenPreview() {
    CronTheme {
        SettingsScreen(onOpenCategory = {})
    }
}
