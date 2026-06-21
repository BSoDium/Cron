package fr.bsodium.cron.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.bsodium.cron.ROUTE_HISTORY
import fr.bsodium.cron.ROUTE_HOME
import fr.bsodium.cron.ui.theme.CronTheme
import fr.bsodium.cron.ui.screens.settings.SETTINGS_ROOT
import fr.bsodium.cron.ui.theme.CronColors
import fr.bsodium.cron.ui.theme.MaterialSymbol
import fr.bsodium.cron.ui.theme.Radius
import fr.bsodium.cron.ui.theme.Spacing
import fr.bsodium.cron.ui.theme.Symbol

private val NAV_SLOT_SIZE = 48.dp
private val NAV_INDICATOR_SIZE = 44.dp

@Composable
internal fun NavPill(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
) {
    Surface(
        color = CronColors.elementSurface,
        shape = Radius.full,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.xs + Spacing.xxs, vertical = Spacing.xs + Spacing.xxs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavSlot(currentRoute, ROUTE_HOME, MaterialSymbol.Alarm, "Home", onNavigate)
            NavSlot(currentRoute, ROUTE_HISTORY, MaterialSymbol.History, "History", onNavigate)
            NavSlot(currentRoute, SETTINGS_ROOT, MaterialSymbol.Settings, "Settings", onNavigate)
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RowScope.NavSlot(
    currentRoute: String?,
    route: String,
    symbol: MaterialSymbol,
    label: String,
    onNavigate: (String) -> Unit,
) {
    val selected = currentRoute == route
    val targetContainer = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    val targetTint = if (selected) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSurfaceVariant
    val colorSpec = MaterialTheme.motionScheme.fastEffectsSpec<Color>()
    val container by animateColorAsState(targetContainer, animationSpec = colorSpec, label = "nav-container")
    val iconTint by animateColorAsState(targetTint, animationSpec = colorSpec, label = "nav-tint")
    val fill by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "nav-fill",
    )
    val haptics = rememberCronHaptics()
    IconTooltip(label) {
        Box(
            modifier = Modifier
                .size(NAV_SLOT_SIZE)
                .clip(Radius.full)
                .clickable(enabled = !selected) {
                    haptics.contextClick()
                    onNavigate(route)
                }
                .semantics {
                    role = Role.Tab
                    this.selected = selected
                    contentDescription = label
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(NAV_INDICATOR_SIZE)
                    .clip(CircleShape)
                    .background(container),
                contentAlignment = Alignment.Center,
            ) {
                Symbol(
                    symbol = symbol,
                    contentDescription = null,
                    tint = iconTint,
                    fill = fill,
                )
            }
        }
    }
}

@Preview(showBackground = true, name = "NavPill — interactive")
@Composable
private fun NavPillPreview() {
    CronTheme {
        var route by remember { mutableStateOf(ROUTE_HOME) }
        NavPill(
            currentRoute = route,
            onNavigate = { route = it },
        )
    }
}
