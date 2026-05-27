package fr.bsodium.cron.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * Floating bottom action bar: a pill housing the three tab icons, optionally
 * paired with a square FAB to the right that hosts the primary action for the
 * current screen (currently only Home, which uses it to re-run the AI plan).
 *
 * Sits above the system gesture/navigation inset rather than docking to the
 * bottom edge, matching the Material 3 Expressive "floating" pattern.
 */
@Composable
fun CronFloatingNav(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    fabAction: FabAction?,
    modifier: Modifier = Modifier,
) {
    val systemBars: PaddingValues = WindowInsets.navigationBars.asPaddingValues()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = systemBars.calculateBottomPadding())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NavPill(currentRoute = currentRoute, onNavigate = onNavigate)
        AnimatedVisibility(
            visible = fabAction != null,
            enter = scaleIn() + fadeIn(),
            exit = scaleOut() + fadeOut(),
        ) {
            Row {
                Box(modifier = Modifier.size(12.dp))
                PrimaryActionFab(fabAction)
            }
        }
    }
}

data class FabAction(
    val onClick: () -> Unit,
    val spinning: Boolean = false,
)

@Composable
private fun NavPill(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(50),
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
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
    val container = if (selected) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.surfaceContainer
    val iconTint = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        color = container,
        shape = RoundedCornerShape(50),
        modifier = Modifier
            .widthIn(min = 56.dp)
            .clickable(enabled = !selected) { onNavigate(route) },
    ) {
        Box(
            modifier = Modifier
                .size(width = 56.dp, height = 44.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = iconTint,
            )
        }
    }
}

@Composable
private fun PrimaryActionFab(action: FabAction?) {
    if (action == null) return
    val transition = rememberInfiniteTransition(label = "fab-spin")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = -360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "fab-spin-angle",
    )
    FloatingActionButton(
        onClick = action.onClick,
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        elevation = FloatingActionButtonDefaults.elevation(
            defaultElevation = 6.dp,
            pressedElevation = 8.dp,
        ),
    ) {
        Icon(
            imageVector = Icons.Filled.PlayArrow,
            contentDescription = "Re-run alarm plan",
            modifier = if (action.spinning) Modifier.rotate(angle) else Modifier,
        )
    }
}
