@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package fr.bsodium.cron.ui.screens.home.components

import android.graphics.Matrix
import android.graphics.Path
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.AlarmOff
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import fr.bsodium.cron.ui.theme.Spacing

internal val BADGE_CLOCK_GAP = Spacing.lg           // collapsed badge ↔ time (breathing room)
internal val BADGE_DIAMETER = 40.dp                 // nests in the 56dp pill cap with an 8dp ring
private const val BADGE_ICON_FRACTION = 0.5f        // alarm icon size relative to the shape bounds

/**
 * Auto-alarms status badge in the collapsed pill: a low-elevation light (`onPrimary`) Material shape
 * (a sunburst) that **rotates** by [rotationDeg] as the card collapses, with an **upright** filled alarm
 * icon in the card's `primary` colour on top — crossed out (AlarmOff) when disabled. The icon does not
 * spin with the shape. Flat (elevation 0).
 */
@Composable
internal fun AlarmStatusBadge(
    enabled: Boolean,
    rotationDeg: Float,
    diameter: Dp,
    modifier: Modifier = Modifier,
) {
    val shapeColor = MaterialTheme.colorScheme.onPrimary
    val morph = remember { Morph(MaterialShapes.VerySunny, MaterialShapes.VerySunny) }
    val path = remember { Path() }
    val matrix = remember { Matrix() }
    Box(modifier = modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawMorph(morph, progress = 0f, rotationDeg = rotationDeg, fillFraction = 1f, color = shapeColor, path = path, matrix = matrix)
        }
        Icon(
            imageVector = if (enabled) Icons.Filled.Alarm else Icons.Filled.AlarmOff,
            contentDescription = if (enabled) "Auto alarms on" else "Auto alarms off",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(diameter * BADGE_ICON_FRACTION),
        )
    }
}
