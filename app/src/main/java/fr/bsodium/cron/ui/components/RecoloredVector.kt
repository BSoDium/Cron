package fr.bsodium.cron.ui.components

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorNode
import androidx.compose.ui.graphics.vector.VectorPath

/**
 * Rebuilds this vector remapping solid colors based on the [VectorPath.name] and original color.
 */
fun ImageVector.rethemed(map: (name: String, Color) -> Color): ImageVector {
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
    root.forEach { builder.addNodeSemantic(it, map) }
    return builder.build()
}

private fun ImageVector.Builder.addNodeSemantic(node: VectorNode, map: (String, Color) -> Color) {
    when (node) {
        is VectorPath -> addPath(
            pathData = node.pathData,
            pathFillType = node.pathFillType,
            name = node.name,
            fill = node.fill.remapSemantic(node.name, map),
            fillAlpha = node.fillAlpha,
            stroke = node.stroke.remapSemantic(node.name, map),
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
            node.forEach { addNodeSemantic(it, map) }
            clearGroup()
        }
    }
}

private fun Brush?.remapSemantic(name: String, map: (String, Color) -> Color): Brush? =
    if (this is SolidColor) SolidColor(map(name, value)) else this

/**
 * Rebuilds this vector with every solid fill AND solid stroke remapped by [map].
 */
fun ImageVector.recolored(map: (Color) -> Color): ImageVector = rethemed { _, color -> map(color) }
