package fr.bsodium.cron.ui.screens.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.AlarmOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.bsodium.cron.ui.theme.Spacing

internal val BADGE_CLOCK_GAP = Spacing.lg           // collapsed badge ↔ time (breathing room)
internal val BADGE_DIAMETER = 40.dp                 // nests in the 56dp pill cap with an 8dp ring
private const val BADGE_ICON_FRACTION = 0.6f        // alarm icon size relative to the disc

/**
 * Auto-alarms status indicator shown in the collapsed pill: a flat (elevation-0) solid `onPrimary` disc
 * with an alarm icon in the card's own `primary` colour — plain when [enabled], crossed out (AlarmOff)
 * when disabled. Sized by [diameter]; its rolling spin is applied by the caller via a `graphicsLayer`
 * rotation, so it draws upright here.
 */
@Composable
internal fun AlarmStatusBadge(
    enabled: Boolean,
    diameter: Dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(diameter)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.onPrimary),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (enabled) Icons.Outlined.Alarm else Icons.Outlined.AlarmOff,
            contentDescription = if (enabled) "Auto alarms on" else "Auto alarms off",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(diameter * BADGE_ICON_FRACTION),
        )
    }
}
