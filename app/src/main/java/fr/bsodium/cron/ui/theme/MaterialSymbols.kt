package fr.bsodium.cron.ui.theme

import android.graphics.Paint
import android.util.Log
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import fr.bsodium.cron.R

/**
 * Material Symbols (Rounded), rendered from the bundled variable font with its FILL / wght / GRAD / opsz
 * axes live. [Symbol] is the drop-in replacement for the old `Icon(imageVector = …)` — icons are
 * **outlined by default** (`fill = 0f`); pass `fill` (animatable) to morph toward filled.
 *
 * The `.ttf` is a SUBSET of the official `MaterialSymbolsRounded[FILL,GRAD,opsz,wght].ttf`, carrying only
 * the glyphs below to stay tiny (~115 KB vs ~15 MB). To add an icon: add an entry with its codepoint
 * (from the matching `MaterialSymbolsRounded …codepoints` file in google/material-design-icons), then
 * re-subset with every enum codepoint:
 *
 *   python3 -m fontTools.subset "MaterialSymbolsRounded[FILL,GRAD,opsz,wght].ttf" \
 *     --unicodes=<all codepoints, comma-separated> \
 *     --output-file=app/src/main/res/font/material_symbols_rounded.ttf \
 *     --no-hinting --layout-features='*' --glyph-names --recalc-bounds
 */
enum class MaterialSymbol(val code: String) {
    Alarm("\uE855"),
    AlarmOff("\uE857"),
    ArrowBack("\uE5C4"),
    ArrowForward("\uE5C8"),
    Article("\uEF87"),
    AutoAwesome("\uE65F"),
    Autoplay("\uF6B5"),
    Bedtime("\uF159"),
    Build("\uF8CD"),
    CalendarMonth("\uEBCC"),
    Check("\uE668"),
    Close("\uE5CD"),
    Code("\uE86F"),
    DirectionsBike("\uE52F"),
    DirectionsBus("\uEFF6"),
    DirectionsCar("\uEFF7"),
    DirectionsTransit("\uEFFA"),
    DirectionsWalk("\uE536"),
    EventUpcoming("\uF238"),
    ExpandLess("\uE5CE"),
    ExpandMore("\uE5CF"),
    History("\uE8B3"),
    Info("\uE88E"),
    Keyboard("\uE312"),
    LightMode("\uE518"),
    LocationOn("\uF1DB"),
    NotificationImportant("\uE004"),
    Person("\uF0D3"),
    PlayArrow("\uE037"),
    RocketLaunch("\uEB9B"),
    Schedule("\uEFD6"),
    Science("\uEA4B"),
    SearchActivity("\uF3E5"),
    Settings("\uE8B8"),
    Shield("\uE9E0"),
    Snooze("\uE046"),
    Stop("\uE047"),
    Timer("\uE425"),
    Update("\uE923"),
    Vibration("\uF2CB"),
    VitalSigns("\uE650"),
    Warning("\uF083"),
    Weekend("\uE16B"),
}

/** Resolves the bundled symbols font to a [Typeface] once per resolver (axes ride on the draw Paint). */
@Composable
private fun rememberSymbolTypeface(): Typeface? {
    val resolver = LocalFontFamilyResolver.current
    return remember(resolver) {
        runCatching {
            resolver.resolve(
                fontFamily = FontFamily(Font(R.font.material_symbols_rounded)),
                fontWeight = FontWeight.Normal,
                fontStyle = FontStyle.Normal,
                fontSynthesis = FontSynthesis.None,
            ).value as? Typeface
        }
            .onFailure { Log.w("MaterialSymbols", "symbols font resolution failed — icons will not render", it) }
            .getOrNull()
    }
}

/**
 * Renders a Material Symbol as a font glyph. Drop-in for `Icon`: pass [tint] and [size] the same way.
 * [fill] 0→1 morphs outlined→filled (animate it for selection states); [weight]/[grade] tune stroke and
 * emphasis; `opsz` tracks [size] for optical correctness. [autoMirror] flips the glyph under RTL (the
 * replacement for the old `Icons.AutoMirrored.*`).
 *
 * Drawn straight onto the canvas rather than via `Text`: Material Symbol glyphs are centred on the em
 * (design centre = em/2 above the baseline), but the font's line metrics are top-heavy and the ink sits
 * entirely above the baseline, so a `Text` line box centres the glyph low. Placing the baseline at the
 * box bottom maps the em exactly onto the [size] box → glyph centred, independent of those metrics. The
 * px-derived `textSize` also keeps the icon fixed-size under the user's font-scale setting, and the axes
 * ride on the Paint (no per-frame `FontFamily` rebuild for an animated [fill]).
 */
@Composable
fun Symbol(
    symbol: MaterialSymbol,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    tint: Color = LocalContentColor.current,
    fill: Float = 0f,
    weight: Int = 400,
    grade: Int = 0,
    autoMirror: Boolean = false,
) {
    val typeface = rememberSymbolTypeface()
    val mirror = autoMirror && LocalLayoutDirection.current == LayoutDirection.Rtl
    val opticalSize = size.value.coerceIn(20f, 48f)
    Canvas(
        modifier = modifier
            .size(size)
            .clearAndSetSemantics {
                if (contentDescription != null) {
                    this.contentDescription = contentDescription
                    this.role = Role.Image
                }
            },
    ) {
        val px = size.toPx()
        val paint = Paint().apply {
            isAntiAlias = true
            this.typeface = typeface
            textSize = px
            textAlign = Paint.Align.CENTER
            color = tint.toArgb()
            // Float.toString is locale-independent ('.'), so this needs no Locale guard.
            fontVariationSettings = "'FILL' $fill,'wght' $weight,'GRAD' $grade,'opsz' $opticalSize"
        }
        drawIntoCanvas { canvas ->
            val nc = canvas.nativeCanvas
            if (mirror) {
                val count = nc.save()
                nc.scale(-1f, 1f, px / 2f, px / 2f)
                nc.drawText(symbol.code, px / 2f, px, paint)
                nc.restoreToCount(count)
            } else {
                nc.drawText(symbol.code, px / 2f, px, paint)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Preview(showBackground = true, name = "Material Symbols — outlined")
@Composable
private fun SymbolGalleryOutlinedPreview() {
    CronTheme {
        FlowRow(
            modifier = Modifier.padding(Spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            MaterialSymbol.entries.forEach { s ->
                Symbol(symbol = s, contentDescription = s.name, size = 28.dp, tint = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Preview(showBackground = true, name = "Material Symbols — filled + bold")
@Composable
private fun SymbolGalleryFilledPreview() {
    CronTheme {
        FlowRow(
            modifier = Modifier.padding(Spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            MaterialSymbol.entries.forEach { s ->
                Symbol(
                    symbol = s,
                    contentDescription = s.name,
                    size = 28.dp,
                    tint = MaterialTheme.colorScheme.primary,
                    fill = 1f,
                    weight = 600,
                )
            }
        }
    }
}
