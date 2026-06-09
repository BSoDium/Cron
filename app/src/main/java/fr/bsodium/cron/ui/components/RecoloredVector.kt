package fr.bsodium.cron.ui.components

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorNode
import androidx.compose.ui.graphics.vector.VectorPath

/**
 * Rebuilds this vector with every solid fill AND solid stroke remapped by [map]. Used to retint a
 * multi-color asset (the onboarding illustration, the no-plan illustration) onto the live Material You
 * `colorScheme` at the Compose layer — the app's framework `Theme.Cron` can't expose the dynamic palette
 * to the drawable via `?attr/color*`. Gradient brushes pass through unchanged; alpha/`pathData` preserved.
 */
fun ImageVector.recolored(map: (Color) -> Color): ImageVector {
    val builder = ImageVector.Builder(
        name = name,
        defaultWidth = defaultWidth,
        defaultHeight = defaultHeight,
        viewportWidth = viewportWidth,
        viewportHeight = viewportHeight,
        tintColor = tintColor,
        tintBlendMode = tintBlendMode,
        autoMirror = autoMirror,
    )
    root.forEach { builder.addNode(it, map) }
    return builder.build()
}

private fun ImageVector.Builder.addNode(node: VectorNode, map: (Color) -> Color) {
    when (node) {
        is VectorPath -> addPath(
            pathData = node.pathData,
            pathFillType = node.pathFillType,
            name = node.name,
            fill = node.fill.remap(map),
            fillAlpha = node.fillAlpha,
            stroke = node.stroke.remap(map),
            strokeAlpha = node.strokeAlpha,
            strokeLineWidth = node.strokeLineWidth,
            strokeLineCap = node.strokeLineCap,
            strokeLineJoin = node.strokeLineJoin,
            strokeLineMiter = node.strokeLineMiter,
            trimPathStart = node.trimPathStart,
            trimPathEnd = node.trimPathEnd,
            trimPathOffset = node.trimPathOffset,
        )
        is VectorGroup -> {
            addGroup(
                name = node.name,
                rotate = node.rotation,
                pivotX = node.pivotX,
                pivotY = node.pivotY,
                scaleX = node.scaleX,
                scaleY = node.scaleY,
                translationX = node.translationX,
                translationY = node.translationY,
                clipPathData = node.clipPathData,
            )
            node.forEach { addNode(it, map) }
            clearGroup()
        }
    }
}

private fun Brush?.remap(map: (Color) -> Color): Brush? =
    if (this is SolidColor) SolidColor(map(value)) else this
