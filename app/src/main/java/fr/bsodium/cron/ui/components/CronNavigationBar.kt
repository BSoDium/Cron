package fr.bsodium.cron.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.bsodium.cron.ROUTE_HISTORY
import fr.bsodium.cron.ROUTE_HOME
import fr.bsodium.cron.ui.screens.settings.SETTINGS_ROOT
import fr.bsodium.cron.ui.theme.CronColors
import fr.bsodium.cron.ui.theme.CronTheme
import fr.bsodium.cron.ui.theme.MaterialSymbol
import fr.bsodium.cron.ui.theme.Symbol

private data class NavDestination(
    val route: String,
    val symbol: MaterialSymbol,
    val label: String,
)

private val DESTINATIONS = listOf(
    NavDestination(ROUTE_HOME, MaterialSymbol.Alarm, "Home"),
    NavDestination(ROUTE_HISTORY, MaterialSymbol.History, "History"),
    NavDestination(SETTINGS_ROOT, MaterialSymbol.Settings, "Settings"),
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CronNavigationBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
) {
    val haptics = rememberCronHaptics()
    NavigationBar(
        containerColor = CronColors.elementSurface,
        tonalElevation = 0.dp,
    ) {
        for (dest in DESTINATIONS) {
            val selected = currentRoute == dest.route
            val fill by animateFloatAsState(
                targetValue = if (selected) 1f else 0f,
                animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                label = "nav-fill-${dest.route}",
            )
            NavigationBarItem(
                selected = selected,
                onClick = {
                    if (!selected) {
                        haptics.contextClick()
                        onNavigate(dest.route)
                    }
                },
                icon = { Symbol(symbol = dest.symbol, contentDescription = null, fill = fill) },
                label = { Text(dest.label) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Preview(showBackground = true)
@Composable
private fun CronNavigationBarPreview() {
    CronTheme {
        CronNavigationBar(currentRoute = ROUTE_HOME, onNavigate = {})
    }
}
