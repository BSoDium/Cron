package fr.bsodium.cron.ui.screens.home.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikepenz.markdown.compose.components.MarkdownComponentModel
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.MarkdownBulletList
import com.mikepenz.markdown.compose.elements.MarkdownHeader
import com.mikepenz.markdown.compose.elements.MarkdownOrderedList
import com.mikepenz.markdown.compose.elements.MarkdownParagraph
import com.mikepenz.markdown.compose.elements.listDepth
import com.mikepenz.markdown.compose.extendedspans.ExtendedSpans
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.markdownAnimations
import com.mikepenz.markdown.model.markdownExtendedSpans
import com.mikepenz.markdown.model.markdownPadding
import com.mikepenz.markdown.model.rememberMarkdownState
import fr.bsodium.cron.ui.theme.CodeFontFamily
import fr.bsodium.cron.ui.theme.SerifFontFamily
import fr.bsodium.cron.ui.theme.Spacing

/** Martian Mono's x-height + stroke read much larger than the body face's, so inline/block code is scaled
 *  below the body point size — but never below [CODE_MIN_SP], so the smaller sans/thinking code stays legible. */
private const val CODE_FONT_SCALE = 0.68f
private const val CODE_MIN_SP = 11f

/**
 * Themed markdown renderer. The thinking area passes `serif = false` (sans
 * everything); the final response passes `serif = true` (serif body *and*
 * headers). Tables render through [CronMarkdownTable] — an editorial,
 * weighted-column table with horizontal rules only.
 */
@Composable
internal fun MarkdownBlock(
    text: String,
    bodyStyle: TextStyle,
    serif: Boolean,
    modifier: Modifier = Modifier,
    immediate: Boolean = true,
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val surfaceHigh = MaterialTheme.colorScheme.surfaceContainerHigh
    val outline = MaterialTheme.colorScheme.outlineVariant

    val colors = markdownColor(
        text = bodyStyle.color.takeIf { it.alpha > 0f } ?: onSurface,
        codeBackground = surfaceHigh,
        inlineCodeBackground = surfaceHigh,
        dividerColor = outline,
        tableBackground = Color.Transparent,
    )
    val serifize: (TextStyle) -> TextStyle =
        { if (serif) it.copy(fontFamily = SerifFontFamily) else it }
    val codeStyle = bodyStyle.copy(
        fontFamily = CodeFontFamily,
        color = onSurface,
        fontSize = (bodyStyle.fontSize.value * CODE_FONT_SCALE).coerceAtLeast(CODE_MIN_SP).sp,
    )
    val typography = markdownTypography(
        h1 = serifize(MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold, color = onSurface)),
        h2 = serifize(MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold, color = onSurface)),
        h3 = serifize(MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, color = onSurface)),
        h4 = serifize(MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium, color = onSurface)),
        h5 = serifize(MaterialTheme.typography.labelLarge.copy(color = onSurface)),
        h6 = serifize(MaterialTheme.typography.labelMedium.copy(color = onSurfaceVariant)),
        text = bodyStyle,
        paragraph = bodyStyle,
        bullet = bodyStyle,
        list = bodyStyle,
        ordered = bodyStyle,
        quote = bodyStyle.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic),
        code = codeStyle,
        inlineCode = codeStyle,
        textLink = androidx.compose.ui.text.TextLinkStyles(
            style = androidx.compose.ui.text.SpanStyle(color = MaterialTheme.colorScheme.primary),
        ),
    )
    // retainState keeps the last successful render on screen while the next parse runs off-thread, and
    // immediate parses the first frame synchronously — together they kill the blank-flash the library
    // otherwise shows on every content change (per streamed token) and on first compose (expand/load).
    val markdownState = rememberMarkdownState(content = text, retainState = true, immediate = immediate)
    Markdown(
        markdownState = markdownState,
        colors = colors,
        typography = typography,
        // The library adds a uniform `block` spacer after every block; keep it at the tight floor
        // and let paragraphs/headers own their rhythm (headers get a roomy break above).
        padding = markdownPadding(
            block = MD_BLOCK_GAP,
            listItemTop = Spacing.xs,
            listItemBottom = Spacing.xs,
            listIndent = Spacing.lg,
        ),
        // Draw the inline-code background as a rounded chip echoing the "Calling …" tool pill. The
        // band is anchored to each line's baseline and sized from the code font, so it hugs the
        // glyphs in both the serif response and the smaller sans thinking text.
        extendedSpans = markdownExtendedSpans {
            ExtendedSpans(
                InlineCodeChipPainter(
                    chipColor = surfaceHigh,
                    cornerRadius = INLINE_CODE_CORNER,
                    horizontalPadding = INLINE_CODE_PAD_H,
                    aboveBaseline = (codeStyle.fontSize.value * INLINE_CODE_ASCENT_RATIO).sp,
                    belowBaseline = (codeStyle.fontSize.value * INLINE_CODE_DESCENT_RATIO).sp,
                ),
            )
        },
        components = markdownComponents(
            paragraph = { model ->
                MarkdownParagraph(model.content, model.node, Modifier.padding(bottom = MD_PARA_BELOW), bodyStyle)
            },
            heading1 = { model -> SpacedHeader(model, typography.h1, HEADING_GAP[0]) },
            heading2 = { model -> SpacedHeader(model, typography.h2, HEADING_GAP[1]) },
            heading3 = { model -> SpacedHeader(model, typography.h3, HEADING_GAP[2]) },
            heading4 = { model -> SpacedHeader(model, typography.h4, HEADING_GAP[3]) },
            heading5 = { model -> SpacedHeader(model, typography.h5, HEADING_GAP[4]) },
            heading6 = { model -> SpacedHeader(model, typography.h6, HEADING_GAP[5]) },
            // Widen the gap between the bullet/number marker and the list text.
            unorderedList = { model ->
                MarkdownBulletList(
                    content = model.content,
                    node = model.node,
                    style = bodyStyle,
                    depth = model.listDepth,
                    markerModifier = { Modifier.padding(end = LIST_MARKER_GAP) },
                )
            },
            orderedList = { model ->
                MarkdownOrderedList(
                    content = model.content,
                    node = model.node,
                    style = bodyStyle,
                    depth = model.listDepth,
                    markerModifier = { Modifier.padding(end = LIST_MARKER_GAP) },
                )
            },
            table = { model -> CronMarkdownTable(model, bodyStyle) },
        ),
        // No-op the default animateContentSize(): per-segment height animation makes each streamed
        // growth slide instead of appearing crisply, reading as laggy.
        animations = markdownAnimations(animateTextSize = { this }),
        modifier = modifier,
    )
}

/** Per-level header spacing: a section break above, a tighter gap below. */
private class HeadingGap(val top: Dp, val bottom: Dp)

// h1 roomiest, shrinking to h6 which nearly touches its following text. Below-gaps
// stack on top of MD_BLOCK_GAP; the top-gaps separate a header from prior content.
private val HEADING_GAP = listOf(
    HeadingGap(Spacing.md, Spacing.sm),                          // h1 — 12 / 8
    HeadingGap(Spacing.sm + Spacing.xxs, Spacing.xs + Spacing.xxs), // h2 — 10 / 6
    HeadingGap(Spacing.sm, Spacing.xs),                          // h3 — 8 / 4
    HeadingGap(Spacing.xs + Spacing.xxs, Spacing.xxs),           // h4 — 6 / 2
    HeadingGap(Spacing.xs, Spacing.xxs),                         // h5 — 4 / 2
    HeadingGap(Spacing.xs, 0.dp),                                // h6 — 4 / 0
)
private val MD_BLOCK_GAP = Spacing.xxs
// Inline-code chip, tuned to echo the "Calling …" tool pill (rounded). InlineCodeChipPainter trims
// the renderer's injected CODE_SPAN spaces out of the drawn range, so we add our own horizontal gap.
private val INLINE_CODE_CORNER = 6.sp
private val INLINE_CODE_PAD_H = 4.sp
// Band extents above/below the baseline, as a fraction of the code font size — so the chip hugs the
// glyphs proportionally in both the serif and sans contexts. Descent runs deliberately past Martian
// Mono's natural descender (~0.25em) for a little breathing room beneath the baseline.
private const val INLINE_CODE_ASCENT_RATIO = 1.2f
private const val INLINE_CODE_DESCENT_RATIO = 0.42f
private val MD_PARA_BELOW = Spacing.xxs
private val LIST_MARKER_GAP = Spacing.sm

@Composable
private fun SpacedHeader(model: MarkdownComponentModel, style: TextStyle, gap: HeadingGap) {
    Box(modifier = Modifier.padding(top = gap.top, bottom = gap.bottom)) {
        MarkdownHeader(model.content, model.node, style)
    }
}
