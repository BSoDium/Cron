package fr.bsodium.cron.ui.screens.home.components

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.isUnspecified
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastForEachIndexed
import com.mikepenz.markdown.compose.extendedspans.ExtendedSpanPainter
import com.mikepenz.markdown.compose.extendedspans.SpanDrawInstructions

/**
 * Draws the rounded "chip" behind inline code, tuned to echo the `Calling …` tool pill.
 *
 * Two reasons this exists instead of the library's `RoundedCornerSpanPainter`:
 *  - The renderer brackets inline code with an injected space glyph on each side, *inside* the
 *    background span (CODE_SPAN in the library's AnnotatedStringKtx). We trim those spaces out of
 *    the drawn range so a wrapped span can't strand a rounded sliver (the lone leading space) at
 *    the end of the previous line.
 *  - The band is anchored to the text [TextLayoutResult.getLineBaseline] with extents scaled from
 *    the code font size, so it hugs the glyphs in both the serif response and the smaller sans
 *    thinking text — and we control the gap below the baseline independently.
 */
class InlineCodeChipPainter(
    private val chipColor: Color,
    private val cornerRadius: TextUnit,
    private val horizontalPadding: TextUnit,
    private val aboveBaseline: TextUnit,
    private val belowBaseline: TextUnit,
) : ExtendedSpanPainter() {
    private val path = Path()

    override fun decorate(
        span: SpanStyle,
        start: Int,
        end: Int,
        text: AnnotatedString,
        builder: AnnotatedString.Builder,
    ): SpanStyle {
        if (span.background.isUnspecified) return span
        // Exclude the injected leading/trailing space from the drawn range, but still strip the
        // background off the whole span so the text layer paints no rectangle behind it.
        val drawStart = (start + 1).coerceAtMost(end)
        val drawEnd = (end - 1).coerceAtLeast(drawStart)
        if (drawStart < drawEnd) builder.addStringAnnotation(TAG, annotation = "", start = drawStart, end = drawEnd)
        return span.copy(background = Color.Unspecified)
    }

    // Inline code carries no link; leave link styling untouched.
    override fun decorate(
        linkAnnotation: LinkAnnotation,
        start: Int,
        end: Int,
        text: AnnotatedString,
        builder: AnnotatedString.Builder,
    ): LinkAnnotation = linkAnnotation

    override fun drawInstructionsFor(layoutResult: TextLayoutResult, color: Color?): SpanDrawInstructions {
        val text = layoutResult.layoutInput.text
        val annotations = text.getStringAnnotations(TAG, start = 0, end = text.length)

        return SpanDrawInstructions {
            val radius = CornerRadius(cornerRadius.toPx())
            val hPad = horizontalPadding.toPx()
            val above = aboveBaseline.toPx()
            val below = belowBaseline.toPx()

            annotations.fastForEach { annotation ->
                val boxes = layoutResult.getBoundingBoxes(annotation.start, annotation.end)
                boxes.fastForEachIndexed { index, box ->
                    val line = layoutResult.getLineForVerticalPosition((box.top + box.bottom) / 2f)
                    val baseline = layoutResult.getLineBaseline(line)
                    path.rewind()
                    path.addRoundRect(
                        RoundRect(
                            rect = Rect(
                                left = box.left - hPad,
                                top = baseline - above,
                                right = box.right + hPad,
                                bottom = baseline + below,
                            ),
                            // Round only the outer corners so a wrapped chip reads as one shape.
                            topLeft = if (index == 0) radius else CornerRadius.Zero,
                            bottomLeft = if (index == 0) radius else CornerRadius.Zero,
                            topRight = if (index == boxes.lastIndex) radius else CornerRadius.Zero,
                            bottomRight = if (index == boxes.lastIndex) radius else CornerRadius.Zero,
                        ),
                    )
                    drawPath(path = path, color = chipColor, style = Fill)
                }
            }
        }
    }

    companion object {
        private const val TAG = "inline_code_chip"
    }
}
