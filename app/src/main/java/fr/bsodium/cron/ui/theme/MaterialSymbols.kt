package fr.bsodium.cron.ui.theme

import android.content.Context
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
import androidx.compose.ui.platform.LocalContext
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
import java.io.File

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
    NavigateNext("\uE409"),
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
    Error("\uE000"),
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

private data class VariationKey(val weight: Int, val grade: Int, val opticalSize: Float)

/** `Typeface.Builder` has no constructor that wraps an already-resolved [Typeface] or reads a `res/font`
 *  id directly — only a raw [File]/`FileDescriptor`/asset path. [get] extracts the bundled subset once
 *  per process into the app's cache dir so [rememberVariedTypeface] has a real file to rebuild from.
 *  `openRawResource` accepts any compiled resource's raw byte blob regardless of its declared type — a
 *  `res/font` ttf id works exactly like a `res/raw` one here, lint's `@RawRes` contract just doesn't
 *  know that. */
private object VariedFontFile {
    @Volatile private var cached: File? = null

    @Suppress("ResourceType")
    fun get(context: Context): File = cached ?: synchronized(this) {
        cached ?: File(context.cacheDir, "material_symbols_rounded.ttf").also { file ->
            if (!file.exists()) {
                context.resources.openRawResource(R.font.material_symbols_rounded).use { input ->
                    file.outputStream().use { output -> input.copyTo(output) }
                }
            }
        }.also { cached = it }
    }
}

/** Bakes `wght`/`GRAD`/`opsz` into a real [Typeface] variant, cached per [VariationKey], instead of a
 *  live per-Paint `fontVariationSettings` string. A per-Paint override isn't guaranteed to resolve
 *  identically on [Paint.getTextBounds]'s measurement path and [android.graphics.Canvas.drawText]'s
 *  render path on every OEM/API level — for a compound glyph (e.g. a bell-plus-slash icon like
 *  [MaterialSymbol.AlarmOff]) where those axes reshape one part relative to another, that divergence
 *  shows up as a visible centering offset; a simple convex glyph's silhouette barely changes shape
 *  across the axes, so the same divergence stays invisible (see docs/color-roles.md Round 12). Baking
 *  the axes into the [Typeface] itself makes both paths resolve the same physical glyph outline.
 *  `fill` is deliberately excluded — it's animated per-frame elsewhere ([CronNavigationBar]'s
 *  tab-selection morph) and mostly affects interior strokework, not the outer bbox centering depends
 *  on, so it stays on the cheap per-Paint path instead of rebuilding a [Typeface] every frame. */
@Composable
private fun rememberVariedTypeface(fallback: Typeface?, weight: Int, grade: Int, opticalSize: Float): Typeface? {
    val context = LocalContext.current
    val key = VariationKey(weight, grade, opticalSize)
    return remember(key) {
        runCatching {
            Typeface.Builder(VariedFontFile.get(context))
                .setFontVariationSettings("'wght' $weight,'GRAD' $grade,'opsz' $opticalSize")
                .build()
        }
            .onFailure { e -> Log.w("MaterialSymbols", "variation axis bake failed for $key — falling back to unvaried typeface", e) }
            .getOrNull()
    } ?: fallback
}

/**
 * Renders a Material Symbol as a font glyph. Drop-in for `Icon`: pass [tint] and [size] the same way.
 * [fill] 0→1 morphs outlined→filled (animate it for selection states); [weight]/[grade] tune stroke and
 * emphasis; `opsz` tracks [size] for optical correctness. [autoMirror] flips the glyph under RTL (the
 * replacement for the old `Icons.AutoMirrored.*`).
 *
 * Drawn straight onto the canvas rather than via `Text`: a `Text` line box centres the glyph using line
 * metrics, which sit low for this font. Instead, [Paint.getTextBounds] measures the glyph's actual ink
 * rectangle for the current size/variation settings, and the draw origin is placed so that rectangle's
 * centre lands on the box's centre — this stays correct regardless of a specific font's side-bearings or
 * baseline assumptions, unlike a fixed `(size/2, size)` origin (which measured ~3px off-centre for this
 * bundled subset font; see docs/color-roles.md Round 10). The px-derived `textSize` also keeps the icon
 * fixed-size under the user's font-scale setting, and the axes ride on the Paint (no per-frame
 * `FontFamily` rebuild for an animated [fill]).
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
    val baseTypeface = rememberSymbolTypeface()
    val mirror = autoMirror && LocalLayoutDirection.current == LayoutDirection.Rtl
    val opticalSize = size.value.coerceIn(20f, 48f)
    val typeface = rememberVariedTypeface(baseTypeface, weight, grade, opticalSize)
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
            textAlign = Paint.Align.LEFT
            color = tint.toArgb()
            // Float.toString is locale-independent ('.'), so this needs no Locale guard.
            fontVariationSettings = "'FILL' $fill"
        }
        val inkBounds = android.graphics.Rect()
        paint.getTextBounds(symbol.code, 0, symbol.code.length, inkBounds)
        val originX = px / 2f - inkBounds.exactCenterX()
        val originY = px / 2f - inkBounds.exactCenterY()
        drawIntoCanvas { canvas ->
            val nc = canvas.nativeCanvas
            if (mirror) {
                val count = nc.save()
                nc.scale(-1f, 1f, px / 2f, px / 2f)
                nc.drawText(symbol.code, originX, originY, paint)
                nc.restoreToCount(count)
            } else {
                nc.drawText(symbol.code, originX, originY, paint)
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
