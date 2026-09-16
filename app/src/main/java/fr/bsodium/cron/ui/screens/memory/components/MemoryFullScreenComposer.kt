package fr.bsodium.cron.ui.screens.memory.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
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
private val SEND_BUTTON_SIZE = 64.dp
private val SEND_ICON_SIZE = 28.dp
private val EDGE_FADE_HEIGHT = 40.dp
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
        enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
        exit = fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec()),
        modifier = modifier,
        label = "memory-fullscreen-composer",
    ) {
        val sendVisible = enabled && value.trim().length >= SEND_VISIBLE_MIN_LENGTH
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
                        .fadingScrollEdges(scrollState),
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
                enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                exit = fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec()),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .imePadding()
                    .padding(bottom = Spacing.xxxl),
                label = "memory-send-visibility",
            ) {
                Box(
                    modifier = Modifier
                        .size(SEND_BUTTON_SIZE)
                        .clip(Radius.full)
                        .background(scheme.primary)
                        .clickable(enabled = sendVisible) {
                            haptics.confirm()
                            onSend()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Symbol(
                        symbol = MaterialSymbol.ArrowForward,
                        contentDescription = "Send",
                        tint = scheme.onPrimary,
                        size = SEND_ICON_SIZE,
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

/** Fades the top and/or bottom [height] of scrollable content to transparent, gated on whether
 *  [scrollState] actually has more to reveal that way — same offscreen-layer + [BlendMode.DstIn]
 *  technique as [fr.bsodium.cron.ui.screens.home.components.fadeBottom], generalized to both edges
 *  and driven by real scroll position instead of an animated collapse-affordance strength. Only
 *  costs the extra composite layer while this box is actually scrollable; a short instruction that
 *  fits on screen never pays for it since both edge checks are false. */
private fun Modifier.fadingScrollEdges(scrollState: ScrollState, height: Dp = EDGE_FADE_HEIGHT): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val edgePx = height.toPx()
        if (scrollState.canScrollBackward) {
            drawRect(
                brush = Brush.verticalGradient(colors = listOf(Color.Transparent, Color.Black), startY = 0f, endY = edgePx),
                blendMode = BlendMode.DstIn,
            )
        }
        if (scrollState.canScrollForward) {
            drawRect(
                brush = Brush.verticalGradient(colors = listOf(Color.Black, Color.Transparent), startY = size.height - edgePx, endY = size.height),
                blendMode = BlendMode.DstIn,
            )
        }
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
