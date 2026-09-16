package fr.bsodium.cron.ui.screens.memory.components

import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.bsodium.cron.ui.components.rememberCronHaptics
import fr.bsodium.cron.ui.theme.CronTheme
import fr.bsodium.cron.ui.theme.CronTypography
import fr.bsodium.cron.ui.theme.MaterialSymbol
import fr.bsodium.cron.ui.theme.Radius
import fr.bsodium.cron.ui.theme.Spacing
import fr.bsodium.cron.ui.theme.Symbol

private val MAX_FONT_SIZE = 34.sp
private val MIN_FONT_SIZE = 18.sp
private val FONT_STEP = 1.sp
// CronTypography.bodySerif's own 26sp/18sp ratio, carried to every stepped-down size so line
// spacing stays proportional instead of cramping at the smaller end.
private const val LINE_HEIGHT_RATIO = 26f / 18f
private const val SEND_VISIBLE_MIN_LENGTH = 2
private val SEND_BUTTON_HEIGHT = 64.dp
private val SEND_BUTTON_BOTTOM_PADDING = Spacing.xl
private const val SEND_LABEL = "Remember this"
private val EDGE_FADE_HEIGHT = 40.dp
private val TOP_EDGE_FADE_HEIGHT = 96.dp
// How much of the fade band stays fully erased before ramping to opaque — a plain linear gradient
// is still half-visible at its midpoint, which read as too weak once content needs to disappear
// behind the status bar or the send button rather than just softly trail off.
private const val STRONG_FADE_HOLD = 0.55f
private const val PLACEHOLDER = "Tell Cron something to remember"

/** [MemoryComposerFab]'s expanded destination: the entire screen becomes the input, dimming to
 *  the page background (not a darkening scrim) rather than the list staying visible underneath —
 *  the list is meant to visually dissolve away, not just get covered. Big, centred text in
 *  [CronTypography.bodySerif] — the same literary serif the chat's own AI prose uses — that shrinks
 *  to fit as it grows past one line (manual step-down measurement, not [BasicTextField] autoSize —
 *  that overload only exists on the read-only `BasicText`, not the editable field). Shrinking stops
 *  at [MIN_FONT_SIZE]: past that, an unbounded dump of text scrolls instead of continuing to shrink
 *  into illegibility, vertically centred until it's long enough to need that scroll. A rounded send
 *  button fades in once a few characters land. Every transition here is a plain crossfade — no
 *  shape-morph, no slide — per explicit design direction: bold and modern, not showy; finer motion
 *  polish is deferred. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
@Composable
internal fun MemoryFullScreenComposer(
    visible: Boolean,
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onDismiss: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val haptics = rememberCronHaptics()
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val imeVisible = WindowInsets.isImeVisible
    var hasShownKeyboard by remember { mutableStateOf(false) }

    LaunchedEffect(visible) {
        if (visible) {
            focusRequester.requestFocus()
            keyboardController?.show()
        } else {
            hasShownKeyboard = false
        }
    }
    LaunchedEffect(imeVisible) {
        when {
            imeVisible -> hasShownKeyboard = true
            hasShownKeyboard && visible -> onDismiss()
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(MaterialTheme.motionScheme.slowEffectsSpec()),
        exit = fadeOut(MaterialTheme.motionScheme.slowEffectsSpec()),
        modifier = modifier,
        label = "memory-fullscreen-composer",
    ) {
        val sendVisible = enabled && value.trim().length >= SEND_VISIBLE_MIN_LENGTH
        // Grows to clear the send button's own footprint once it's visible, so content fades away
        // as it scrolls behind the button instead of just at the literal bottom of the screen —
        // and shrinks back to a plain edge fade when the button isn't there to hide behind.
        val bottomFadeHeight by animateDpAsState(
            targetValue = if (sendVisible) {
                SEND_BUTTON_BOTTOM_PADDING + SEND_BUTTON_HEIGHT + EDGE_FADE_HEIGHT
            } else {
                EDGE_FADE_HEIGHT
            },
            animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
            label = "memory-bottom-fade-height",
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(scheme.background),
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding(),
            ) {
                val density = LocalDensity.current
                val textMeasurer = rememberTextMeasurer()
                val baseStyle = CronTypography.bodySerif.copy(textAlign = TextAlign.Center)
                val boxMaxHeight = maxHeight
                val horizontalPaddingPx = with(density) { (Spacing.xxl * 2).toPx() }
                val maxWidthPx = with(density) { maxWidth.toPx() } - horizontalPaddingPx
                val maxHeightPx = with(density) { boxMaxHeight.toPx() }
                val fontSize = remember(value, maxWidthPx, maxHeightPx) {
                    fittingFontSize(textMeasurer, baseStyle, value.ifEmpty { PLACEHOLDER }, maxWidthPx, maxHeightPx)
                }
                val textStyle = baseStyle.copy(fontSize = fontSize, lineHeight = fontSize * LINE_HEIGHT_RATIO)
                val scrollState = rememberScrollState()
                // Scroll surface spans the full screen edge-to-edge (not just the text column) so
                // the fade bands read as a property of the screen, not the text block — the text
                // itself keeps its own reading margin via the inner Box's padding.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .fadingEdges(topHeight = TOP_EDGE_FADE_HEIGHT, bottomHeight = bottomFadeHeight),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.xxl)
                            .heightIn(min = boxMaxHeight)
                            .wrapContentHeight(Alignment.CenterVertically),
                    ) {
                        if (value.isEmpty()) {
                            Text(
                                text = PLACEHOLDER,
                                style = textStyle,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                        BasicTextField(
                            value = value,
                            onValueChange = onValueChange,
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester),
                            enabled = enabled,
                            textStyle = textStyle.copy(color = scheme.onBackground),
                            cursorBrush = SolidColor(scheme.primary),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = { if (value.isNotBlank()) onSend() }),
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = sendVisible,
                enter = fadeIn(MaterialTheme.motionScheme.slowEffectsSpec()),
                exit = fadeOut(MaterialTheme.motionScheme.slowEffectsSpec()),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .imePadding()
                    .padding(horizontal = Spacing.xxl, vertical = SEND_BUTTON_BOTTOM_PADDING),
                label = "memory-send-visibility",
            ) {
                val sendLabelStyle = MaterialTheme.typography.titleLarge
                val sendIconSize = rememberXHeightDp(sendLabelStyle)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(SEND_BUTTON_HEIGHT)
                        .clip(Radius.full)
                        .background(scheme.primary)
                        .clickable(enabled = sendVisible) {
                            haptics.confirm()
                            onSend()
                        },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = SEND_LABEL,
                        style = sendLabelStyle,
                        color = scheme.onPrimary,
                    )
                    Spacer(Modifier.width(Spacing.sm))
                    Symbol(
                        symbol = MaterialSymbol.ArrowForward,
                        contentDescription = null,
                        tint = scheme.onPrimary,
                        size = sendIconSize,
                    )
                }
            }
        }
    }
}

/** Largest font size (stepping down from [MAX_FONT_SIZE] to [MIN_FONT_SIZE]) whose wrapped layout
 *  fits within the available box — the manual equivalent of legacy Android's auto-size TextView,
 *  since Compose's [androidx.compose.foundation.text.TextAutoSize] only wires into the read-only
 *  `BasicText`, not an editable [androidx.compose.foundation.text.BasicTextField]. Measured against
 *  [baseStyle] (the same [CronTypography.bodySerif]-derived style actually rendered) so the wrap
 *  points this predicts match what's drawn. Returns [MIN_FONT_SIZE] as a floor rather than shrinking
 *  further — the caller scrolls once even that doesn't fit, instead of shrinking text into
 *  illegibility for an unbounded wall of text. */
private fun fittingFontSize(
    textMeasurer: TextMeasurer,
    baseStyle: TextStyle,
    text: String,
    maxWidthPx: Float,
    maxHeightPx: Float,
): TextUnit {
    if (maxWidthPx <= 0f || maxHeightPx <= 0f) return MAX_FONT_SIZE
    var candidate = MAX_FONT_SIZE.value
    while (candidate > MIN_FONT_SIZE.value) {
        val result = textMeasurer.measure(
            text = text,
            style = baseStyle.copy(fontSize = candidate.sp, lineHeight = (candidate * LINE_HEIGHT_RATIO).sp),
            constraints = Constraints(maxWidth = maxWidthPx.toInt()),
        )
        if (result.size.height <= maxHeightPx) break
        candidate -= FONT_STEP.value
    }
    return candidate.coerceAtLeast(MIN_FONT_SIZE.value).sp
}

/** The x-height (ink height of a lowercase "x") of [style] at its resolved size — measured, not
 *  approximated as a fraction of font size, via the same [android.graphics.Paint.getTextBounds]
 *  technique [fr.bsodium.cron.ui.theme.Symbol] already uses to centre glyphs on their own ink rather
 *  than font-box metrics. Used to size the send button's arrow to match the visual weight of its
 *  label's lowercase letters instead of a arbitrarily-picked icon size next to it. */
@Composable
private fun rememberXHeightDp(style: TextStyle): Dp {
    val resolver = LocalFontFamilyResolver.current
    val density = LocalDensity.current
    return remember(resolver, style.fontFamily, style.fontWeight, style.fontSize, density) {
        val typeface = resolver.resolve(
            fontFamily = style.fontFamily,
            fontWeight = style.fontWeight ?: FontWeight.Normal,
            fontStyle = style.fontStyle ?: FontStyle.Normal,
            fontSynthesis = FontSynthesis.None,
        ).value as? Typeface
        val paint = Paint().apply {
            this.typeface = typeface
            textSize = with(density) { style.fontSize.toPx() }
        }
        val inkBounds = Rect()
        paint.getTextBounds("x", 0, 1, inkBounds)
        with(density) { inkBounds.height().toDp() }
    }
}

/** Fades the top and bottom of scrollable content to transparent — same offscreen-layer +
 *  [BlendMode.DstIn] technique as [fr.bsodium.cron.ui.screens.home.components.fadeBottom],
 *  generalized to both edges. Each band holds fully erased for [STRONG_FADE_HOLD] of its height
 *  before ramping to opaque — stronger than a plain linear gradient, which is still half-visible at
 *  its own midpoint — so content is essentially gone by the time it reaches the status bar (top) or
 *  the send button (bottom), not just dimmed.
 *
 *  Deliberately unconditional rather than gated on [ScrollState.canScrollBackward]/
 *  [ScrollState.canScrollForward]: the status bar and the send button sit on top of this content
 *  regardless of scroll position — e.g. typing pins the caret (and so the scroll offset) at the very
 *  end, where `canScrollForward` is always false, which would silently skip exactly the fade this is
 *  for. A short instruction that never reaches either band pays nothing extra either way, since
 *  there's no content there for [BlendMode.DstIn] to erase. */
private fun Modifier.fadingEdges(topHeight: Dp, bottomHeight: Dp): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val topPx = topHeight.toPx()
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color.Transparent,
                STRONG_FADE_HOLD to Color.Transparent,
                1f to Color.Black,
                startY = 0f,
                endY = topPx,
            ),
            blendMode = BlendMode.DstIn,
        )
        val bottomPx = bottomHeight.toPx()
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color.Black,
                (1f - STRONG_FADE_HOLD) to Color.Transparent,
                1f to Color.Transparent,
                startY = size.height - bottomPx,
                endY = size.height,
            ),
            blendMode = BlendMode.DstIn,
        )
    }

@Preview(showBackground = true, name = "Memory full-screen composer — empty")
@Composable
private fun MemoryFullScreenComposerEmptyPreview() {
    CronTheme {
        MemoryFullScreenComposer(visible = true, value = "", onValueChange = {}, onSend = {}, onDismiss = {}, enabled = true)
    }
}

@Preview(showBackground = true, name = "Memory full-screen composer — typing")
@Composable
private fun MemoryFullScreenComposerTypingPreview() {
    CronTheme {
        MemoryFullScreenComposer(
            visible = true,
            value = "I wake up earlier on gym days and prefer the window seat",
            onValueChange = {},
            onSend = {},
            onDismiss = {},
            enabled = true,
        )
    }
}
