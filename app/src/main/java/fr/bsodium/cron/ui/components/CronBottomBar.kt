package fr.bsodium.cron.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import fr.bsodium.cron.ui.theme.BrandOnOrange

/**
 * Custom 3-tab bottom nav. Dark surface, no labels. The selected tab is
 * wrapped in a small white pill containing only its icon — matches the
 * brand mockup. Stock M3 `NavigationBar` is replaced because its
 * mandatory pill+label affordance doesn't match this aesthetic.
 */
@Composable
fun CronBottomBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavSlot(currentRoute, "home", Icons.Filled.Home, "Home", onNavigate)
            NavSlot(currentRoute, "history", Icons.Filled.History, "History", onNavigate)
            NavSlot(currentRoute, "settings", Icons.Filled.Settings, "Settings", onNavigate)
        }
    }
}

@Composable
private fun RowScope.NavSlot(
    currentRoute: String?,
    route: String,
    icon: ImageVector,
    label: String,
    onNavigate: (String) -> Unit,
) {
    val selected = currentRoute == route
    if (selected) {
        Surface(
            color = BrandOnOrange, // pure white
            shape = RoundedCornerShape(50),
            modifier = Modifier
                .height(44.dp)
                .widthIn(min = 72.dp)
                .clickable(enabled = false) {},
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = MaterialTheme.colorScheme.background,
                )
            }
        }
    } else {
        Box(
            modifier = Modifier
                .height(44.dp)
                .widthIn(min = 72.dp)
                .clickable { onNavigate(route) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
