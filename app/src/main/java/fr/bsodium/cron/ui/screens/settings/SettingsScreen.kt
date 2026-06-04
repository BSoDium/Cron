package fr.bsodium.cron.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Weekend
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import fr.bsodium.cron.ui.components.ScreenTitle
import fr.bsodium.cron.ui.screens.settings.components.SettingsCategoryRow
import fr.bsodium.cron.ui.theme.CronTheme
import fr.bsodium.cron.ui.theme.Radius
import fr.bsodium.cron.ui.theme.Spacing

private data class SettingsCategory(
    val route: String,
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
)

/** Categories grouped into Android-Settings-style connected cards: timing · assistant + system · app. */
private val SETTINGS_GROUPS: List<List<SettingsCategory>> = listOf(
    listOf(
        SettingsCategory(SETTINGS_SCHEDULE, Icons.Outlined.Schedule, "Schedule", "When Cron plans tonight's alarm"),
        SettingsCategory(SETTINGS_FREE_DAYS, Icons.Outlined.Weekend, "Free days", "Wake window when you have no events"),
        SettingsCategory(SETTINGS_BUFFERS, Icons.Outlined.Timer, "Buffers", "Travel and preparation time"),
    ),
    listOf(
        SettingsCategory(SETTINGS_ASSISTANT, Icons.Outlined.AutoAwesome, "Assistant", "Instructions, token budget, haptics"),
        SettingsCategory(SETTINGS_RELIABILITY, Icons.Outlined.Shield, "Reliability", "Permissions that keep alarms on time"),
    ),
    listOf(
        SettingsCategory(SETTINGS_ACCOUNT, Icons.Outlined.Person, "Account", "Display name and API key"),
        SettingsCategory(SETTINGS_ABOUT, Icons.Outlined.Info, "About", "Credits and attributions"),
    ),
)

// Connected-card group: large radius on a group's outer corners, a barely-rounded seam where
// same-group cards meet, a tight gap within a group and a wider gap between groups.
private val GROUP_GAP = Spacing.lg
private val CARD_GAP = Spacing.xs
private val GROUP_OUTER_RADIUS = Radius.xl
private val GROUP_INNER_RADIUS = Radius.sm

/** Settings landing: tappable categories as connected cards that drill into per-category sub-screens. */
@Composable
fun SettingsScreen(onOpenCategory: (String) -> Unit) {
    val navBottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val statusInsetTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.xl)
            .padding(top = statusInsetTop + Spacing.xxl, bottom = navBottomInset + Spacing.navBarClearance),
    ) {
        ScreenTitle("Settings")
        Spacer(Modifier.height(Spacing.xl))
        SETTINGS_GROUPS.forEachIndexed { groupIndex, group ->
            if (groupIndex > 0) Spacer(Modifier.height(GROUP_GAP))
            group.forEachIndexed { index, category ->
                if (index > 0) Spacer(Modifier.height(CARD_GAP))
                SettingsCategoryRow(
                    icon = category.icon,
                    title = category.title,
                    subtitle = category.subtitle,
                    shape = groupItemShape(isFirst = index == 0, isLast = index == group.lastIndex),
                    onClick = { onOpenCategory(category.route) },
                )
            }
        }
    }
}

/** Rounds a card's outer (group-edge) corners large and its seams with neighbours small. */
private fun groupItemShape(isFirst: Boolean, isLast: Boolean): RoundedCornerShape {
    val top = if (isFirst) GROUP_OUTER_RADIUS else GROUP_INNER_RADIUS
    val bottom = if (isLast) GROUP_OUTER_RADIUS else GROUP_INNER_RADIUS
    return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    CronTheme {
        SettingsScreen(onOpenCategory = {})
    }
}
