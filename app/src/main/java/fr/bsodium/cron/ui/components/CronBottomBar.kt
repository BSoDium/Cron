package fr.bsodium.cron.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Three-tab bottom navigation for the post-onboarding shell.
 */
@Composable
fun CronBottomBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
) {
    NavigationBar {
        Item(currentRoute, "home", Icons.Filled.Home, "Home", onNavigate)
        Item(currentRoute, "history", Icons.Filled.History, "History", onNavigate)
        Item(currentRoute, "settings", Icons.Filled.Settings, "Settings", onNavigate)
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Item(
    currentRoute: String?,
    route: String,
    icon: ImageVector,
    label: String,
    onNavigate: (String) -> Unit,
) {
    NavigationBarItem(
        selected = currentRoute == route,
        onClick = { if (currentRoute != route) onNavigate(route) },
        icon = { Icon(icon, contentDescription = label) },
        label = { Text(label) },
        alwaysShowLabel = false,
    )
}
