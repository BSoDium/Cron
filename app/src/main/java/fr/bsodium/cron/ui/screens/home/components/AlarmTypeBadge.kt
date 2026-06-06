@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package fr.bsodium.cron.ui.screens.home.components

import android.graphics.Matrix
import android.graphics.Path
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import fr.bsodium.cron.ui.theme.Spacing
import kotlinx.datetime.LocalTime

/** When the alarm wakes the user, picking the badge shape. */
internal enum class AlarmKind { Early, Morning, Late }

/** early = before 6:00, morning = 6:00–8:59, late = 9:00 or later; null when no alarm is set. */
internal fun alarmKindFor(time: LocalTime?): AlarmKind? = when {
    time == null -> null
    time.hour < 6 -> AlarmKind.Early
    time.hour < 9 -> AlarmKind.Morning
    else -> AlarmKind.Late
}

private val AlarmKind.shape: RoundedPolygon
    get() = when (this) {
        AlarmKind.Early -> MaterialShapes.SemiCircle
        AlarmKind.Morning -> MaterialShapes.Sunny
        AlarmKind.Late -> MaterialShapes.Boom
    }

internal val BADGE_DIAMETER = 44.dp
internal val BADGE_DATE_GAP = Spacing.lg            // badge ↔ date in the expanded row
internal val BADGE_CLOCK_GAP = Spacing.lg           // collapsed badge ↔ time (breathing room)

/**
 * The alarm-type indicator: a flat (elevation-0) `onPrimary` disc with the Material [shape] knocked out
 * of it in the card's own `primary` colour, so the shape reads as a hole in a solid disc. Spun by
 * [rotationDeg] (driven by the card's collapse fraction, like a cog).
 */
@Composable
internal fun AlarmTypeBadge(
    kind: AlarmKind,
    rotationDeg: Float,
    modifier: Modifier = Modifier,
    diameter: Dp = BADGE_DIAMETER,
) {
    val disc = MaterialTheme.colorScheme.onPrimary
    val shapeColor = MaterialTheme.colorScheme.primary
    val morph = remember(kind) { Morph(kind.shape, kind.shape) }
    val path = remember { Path() }
    val matrix = remember { Matrix() }
    Box(
        modifier = modifier
            .size(diameter)
            .clip(CircleShape)
            .background(disc),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawMorph(morph, progress = 0f, rotationDeg = rotationDeg, fillFraction = 1f, color = shapeColor, path = path, matrix = matrix)
        }
    }
}
