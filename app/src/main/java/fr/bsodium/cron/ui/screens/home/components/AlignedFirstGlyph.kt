package fr.bsodium.cron.ui.screens.home.components

import android.util.Log
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight

/**
 * Shifts [text] left by the first glyph's side bearing so the painted ink starts at x=0. Lets the
 * Space Grotesk date label and the Major Mono Display digits below it share the same visible left
 * edge without per-font magic.
 */
@Composable
internal fun AlignedFirstGlyph(
    text: String,
    color: Color,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    val resolver = LocalFontFamilyResolver.current
    val density = LocalDensity.current
    val leftBearingPx = remember(text, style, density.density) {
        if (text.isEmpty() || style.fontFamily == null) 0
        else runCatching {
            val typeface = resolver.resolve(
                fontFamily = style.fontFamily,
                fontWeight = style.fontWeight ?: FontWeight.Normal,
                fontStyle = style.fontStyle ?: FontStyle.Normal,
                fontSynthesis = FontSynthesis.None,
            ).value as? Typeface ?: return@runCatching 0
            val paint = Paint().apply {
                this.typeface = typeface
                this.textSize = with(density) { style.fontSize.toPx() }
                this.isAntiAlias = true
            }
            val rect = Rect()
            paint.getTextBounds(text, 0, 1, rect)
            rect.left
        }
            .onFailure { Log.w("AlignedFirstGlyph", "left-bearing measurement failed — glyph unaligned", it) }
            .getOrDefault(0)
    }
    Text(
        text = text,
        color = color,
        style = style,
        maxLines = 1,
        softWrap = false,
        modifier = modifier.layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            val shift = leftBearingPx
            val newWidth = (placeable.width - shift).coerceAtLeast(0)
            layout(newWidth, placeable.height) {
                placeable.place(-shift, 0)
            }
        },
    )
}
