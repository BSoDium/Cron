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
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import fr.bsodium.cron.ui.theme.Radius
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

// Gap between the badge's round background and the card edges/corner. With this gap the round
// background of diameter 2*(Radius.xl − gap) is concentric with the card corner (radius Radius.xl) and
// with the collapsed pill's left circle (same radius) — so it nests pixel-perfectly in BOTH states.
internal val BADGE_CORNER_GAP = Spacing.sm
internal val BADGE_DIAMETER = (Radius.xl - BADGE_CORNER_GAP) * 2
// Extra start-padding on the date so it begins just past the badge (badge right edge + a small gap),
// relative to the content's existing Spacing.xxl start padding.
internal val BADGE_DATE_INSET = (BADGE_CORNER_GAP + BADGE_DIAMETER + Spacing.sm - Spacing.xxl)

private const val BADGE_BG_ALPHA = 0.16f

/**
 * The alarm-type indicator: a zero-elevation round background holding a Material [shape], drawn inside
 * with a small margin and spun by [rotationDeg] (driven by the card's collapse fraction, like a cog).
 */
@Composable
internal fun AlarmTypeBadge(
    kind: AlarmKind,
    rotationDeg: Float,
    modifier: Modifier = Modifier,
    diameter: Dp = BADGE_DIAMETER,
) {
    val onCard = MaterialTheme.colorScheme.onPrimary
    val morph = remember(kind) { Morph(kind.shape, kind.shape) }
    val path = remember { Path() }
    val matrix = remember { Matrix() }
    Box(
        modifier = modifier
            .size(diameter)
            .clip(CircleShape)
            .background(onCard.copy(alpha = BADGE_BG_ALPHA)),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawMorph(morph, progress = 0f, rotationDeg = rotationDeg, fillFraction = 1f, color = onCard, path = path, matrix = matrix)
        }
    }
}
