package fr.bsodium.cron.ui.screens.home.components

import fr.bsodium.cron.ui.theme.LCD_FONT_SIZE
import android.util.Log
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import fr.bsodium.cron.ui.theme.LcdFontFamily

/**
 * Visible-ink vertical centre and height of the LCD digits, as fractions of the text line box, plus
 * the line-box height in px (at 76sp) so callers can derive ink px without re-measuring the glyph.
 */
internal data class LcdInkMetrics(
    val centerFraction: Float,
    val heightFraction: Float,
    val lineBoxPx: Float,
)

/**
 * Measures where the LCD digit ink actually sits inside its line box. Major Mono Display has no
 * descenders, so the digits sit high in the box and the box centre is NOT the visual centre — these
 * fractions let callers centre the digits (and the colon) pixel-perfectly. Resolution-independent
 * (metrics scale linearly with size), so safe to reuse at any rendered size.
 */
@Composable
internal fun rememberLcdInkMetrics(): LcdInkMetrics {
    val resolver = LocalFontFamilyResolver.current
    val density = LocalDensity.current
    return remember(resolver, density.density) {
        val fallback = LcdInkMetrics(
            centerFraction = 0.5f,
            heightFraction = 0.7f,
            lineBoxPx = with(density) { LCD_FONT_SIZE.toPx() },
        )
        runCatching {
            val typeface = resolver.resolve(
                fontFamily = LcdFontFamily,
                fontWeight = FontWeight.Normal,
                fontStyle = FontStyle.Normal,
                fontSynthesis = FontSynthesis.None,
            ).value as? Typeface ?: return@runCatching fallback
            val paint = Paint().apply {
                this.typeface = typeface
                this.textSize = with(density) { LCD_FONT_SIZE.toPx() }
                this.isAntiAlias = true
            }
            val fm = paint.fontMetrics
            val lineBox = fm.descent - fm.ascent
            if (lineBox <= 0f) return@runCatching fallback
            val bounds = Rect()
            paint.getTextBounds("0", 0, 1, bounds)
            val baselineFromTop = -fm.ascent
            val inkTop = baselineFromTop + bounds.top
            val inkBottom = baselineFromTop + bounds.bottom
            LcdInkMetrics(
                centerFraction = (((inkTop + inkBottom) / 2f) / lineBox).coerceIn(0f, 1f),
                heightFraction = ((inkBottom - inkTop) / lineBox).coerceIn(0.1f, 1f),
                lineBoxPx = lineBox,
            )
        }
            .onFailure { Log.w("LcdMetrics", "LCD ink metrics measurement failed — using fallback ratios", it) }
            .getOrDefault(fallback)
    }
}
