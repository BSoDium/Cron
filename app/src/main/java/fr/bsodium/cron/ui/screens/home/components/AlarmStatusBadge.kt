@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package fr.bsodium.cron.ui.screens.home.components

import android.graphics.Matrix
import android.graphics.Path
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
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
        AlarmKind.Early -> MaterialShapes.Circle
        AlarmKind.Morning -> MaterialShapes.SemiCircle
        AlarmKind.Late -> MaterialShapes.VerySunny
    }

internal val BADGE_CLOCK_GAP = Spacing.lg           // collapsed badge ↔ time (breathing room)
internal val BADGE_DIAMETER = 40.dp                 // nests in the 56dp pill cap with an 8dp ring

/**
 * The alarm-type indicator: a flat (elevation-0) solid `onPrimary` disc filled by the Material shape in
 * the card's own `primary` colour. Sized by [diameter]. Only shown in the collapsed pill; its rolling
 * spin is applied by the caller via a `graphicsLayer` rotation, so it draws upright here.
 */
@Composable
internal fun AlarmTypeBadge(
    kind: AlarmKind,
    diameter: Dp,
    modifier: Modifier = Modifier,
) {
    val disc = MaterialTheme.colorScheme.onPrimary
    val shapeColor = MaterialTheme.colorScheme.primary
    val morph = remember(kind) { Morph(kind.shape, kind.shape) }
    val path = remember { Path() }
    val matrix = remember { Matrix() }
    Canvas(modifier = modifier.size(diameter)) {
        drawCircle(color = disc, radius = size.minDimension / 2f, center = Offset(size.width / 2f, size.height / 2f))
        drawMorph(morph, progress = 0f, rotationDeg = 0f, fillFraction = 1f, color = shapeColor, path = path, matrix = matrix)
    }
}
