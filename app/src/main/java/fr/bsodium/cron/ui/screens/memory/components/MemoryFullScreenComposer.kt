package fr.bsodium.cron.ui.screens.memory.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
// Close to the label's own cap height (titleLarge is 22sp) rather than a fixed "big icon" size —
// at 32dp the arrow read as oversized/disconnected next to the text; matching it to the text's own
// scale is what actually reads as one coherent lockup instead of two separately-sized elements.
private val SEND_ICON_SIZE = 22.dp
// Thinner than the label's own weight (deliberately, not matched to it) — a thick arrow at
// SEND_ICON_SIZE read as heavy/rounded rather than crisp; this is the Rounded family's own lightest
// practical weight before strokes start looking broken up.
private const val SEND_ICON_WEIGHT = 500
// Derived, not hardcoded: this equals the icon's vertical centering gap ((height - iconSize) / 2) by
// construction, so the icon sits with equal padding on its top, bottom, and trailing edge instead of
// that only happening to match at today's SEND_BUTTON_HEIGHT/SEND_ICON_SIZE values.
private val SEND_ICON_END_PADDING = (SEND_BUTTON_HEIGHT - SEND_ICON_SIZE) / 2
private const val SEND_LABEL = "Remember this"
// ButtonGroupDefaults.ExpandedRatio (0.15f) expands the pressed child by 15% of *its own* width —
// fine for same-sized siblings, but the send pill is many times wider than the fixed-size cancel
// circle, so 15% of the pill's width is a huge absolute delta to subtract from the circle's small
// budget: pressing send nearly erased cancel. A much smaller ratio keeps the bounce noticeable
// without the neighbour collapsing.
private const val BUTTON_GROUP_EXPANDED_RATIO = 0.04f
private val CANCEL_ICON_SIZE = 24.dp
// Fixed rather than derived from the viewport, so shrinking behaves the same whether the keyboard
// is up or not — tying it to the (keyboard-dependent) available height meant the text had to grow
// to fill nearly the whole screen before shrinking ever kicked in, since the viewport itself is
// that tall. This is roughly 5 lines at MAX_FONT_SIZE / 9 lines at MIN_FONT_SIZE: short entries stay
// big and centred, longer ones shrink first and only scroll once shrinking alone can't fit them.
private val TEXT_FIT_HEIGHT = 240.dp
private val TOP_EDGE_FADE_HEIGHT = 96.dp
// Sized to fully cover the floating button row's own footprint (plus headroom) rather than matching
// it pixel-for-pixel — see the fade Boxes below for why an approximate, static band is preferable to
// a precisely reserved one.
private val BOTTOM_EDGE_FADE_HEIGHT = SEND_BUTTON_HEIGHT + SEND_BUTTON_BOTTOM_PADDING * 2 + 40.dp
// Real scroll-content padding, not just a visual fade — this is what actually keeps a scrolled-to-
// the-edge line of text out from under the curtains rather than merely dimmed underneath them. Sized
// a bit past each curtain's own height so the text clears the curtain entirely, not just its opaque
// core.
private val SCROLL_TOP_PADDING = TOP_EDGE_FADE_HEIGHT + Spacing.xl
private val SCROLL_BOTTOM_PADDING = BOTTOM_EDGE_FADE_HEIGHT + Spacing.xl
/** Consistent button squish amount */
private val SQUISH_AMOUNT = 12.dp

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
 *  into illegibility, vertically centred until it's long enough to need that scroll. A cancel button
 *  sits beside the send pill (always visible, disabled rather than hidden until there's text) so
 *  backing out is always explicit — closing this way clears [value] rather than leaving a stale draft
 *  for next time. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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
    val cancel = {
        keyboardController?.hide()
        onDismiss()
    }

    // Only an explicit Cancel/Send/system-back closes this screen — dismissing the keyboard (e.g.
    // swiping it down to review the full page) must NOT close it. An earlier version watched
    // WindowInsets.isImeVisible and auto-dismissed on every keyboard hide, which closed the whole
    // composer the instant the keyboard so much as flickered — confirmed live, not theoretical.
    BackHandler(enabled = visible, onBack = cancel)

    val transitionState = remember { MutableTransitionState(false) }
    LaunchedEffect(visible) { transitionState.targetState = visible }
    // Clearing the draft only once the exit animation has fully settled — not on the cancel click
    // itself — keeps the placeholder from flashing back over the still-fading-out typed text.
    LaunchedEffect(transitionState.currentState, transitionState.targetState) {
        if (!transitionState.currentState && !transitionState.targetState) onValueChange("")
    }

    AnimatedVisibility(
        visibleState = transitionState,
        enter = fadeIn(MaterialTheme.motionScheme.slowEffectsSpec()),
        exit = fadeOut(MaterialTheme.motionScheme.slowEffectsSpec()),
        modifier = modifier,
        label = "memory-fullscreen-composer",
    ) {
        val sendEnabled = enabled && value.trim().length >= SEND_VISIBLE_MIN_LENGTH
        // Requesting focus from here (once this content is actually in composition) rather than from
        // an effect keyed on the outer `visible` flag — that effect fired the instant `visible` flipped
        // true, before AnimatedVisibility had composed the BasicTextField owning focusRequester, so the
        // request silently landed on nothing and the keyboard never opened. LaunchedEffect(Unit) here
        // reruns every time this content re-enters composition, i.e. every time the composer opens.
        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
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
                // The height actually free for text once both curtains' real scroll-padding is
                // excluded — short text centers within just this region, not the full viewport, so it
                // doesn't visually sit closer to one curtain than the other.
                val clearHeight = (boxMaxHeight - SCROLL_TOP_PADDING - SCROLL_BOTTOM_PADDING).coerceAtLeast(0.dp)
                val horizontalPaddingPx = with(density) { (Spacing.xxl * 2).toPx() }
                val maxWidthPx = with(density) { maxWidth.toPx() } - horizontalPaddingPx
                val maxHeightPx = with(density) { minOf(TEXT_FIT_HEIGHT, clearHeight).toPx() }
                val fontSize = remember(value, maxWidthPx, maxHeightPx) {
                    fittingFontSize(textMeasurer, baseStyle, value.ifEmpty { PLACEHOLDER }, maxWidthPx, maxHeightPx)
                }
                val textStyle = baseStyle.copy(fontSize = fontSize, lineHeight = fontSize * LINE_HEIGHT_RATIO)
                val scrollState = rememberScrollState()
                // BasicTextField's own bring-cursor-into-view behavior doesn't reach this scroll
                // container reliably once the text overflows the fixed-height centred box below —
                // confirmed live: scrollState.maxValue correctly grows past 0, but .value never
                // followed, leaving whatever was just typed hidden behind the send row. Driving the
                // scroll explicitly on every text change is what actually keeps the caret visible. An
                // animated scroll restarts (and so never catches up) on every keystroke of continuous
                // typing — confirmed live the animated version still lagged — so this jumps instantly.
                // Landing on maxValue is exactly right once SCROLL_TOP_PADDING/SCROLL_BOTTOM_PADDING
                // are real content (below) rather than just a fade drawn over the text: maxValue then
                // means "the last line is SCROLL_BOTTOM_PADDING clear of the true bottom edge", not
                // "the last line is flush against it".
                LaunchedEffect(value, scrollState.maxValue) {
                    scrollState.scrollTo(scrollState.maxValue)
                }
                // Spans the full viewport, including behind the floating buttons — the static fade
                // overlays and the buttons themselves (later siblings below, so drawn on top) are what
                // visually hide content there. SCROLL_TOP_PADDING/SCROLL_BOTTOM_PADDING below are what
                // actually keep the text itself clear of that zone once fully scrolled — the curtains
                // alone only dimmed it, they didn't stop text from sliding under the buttons.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState),
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Spacer(Modifier.height(SCROLL_TOP_PADDING))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Spacing.xxl)
                                .heightIn(min = clearHeight)
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
                        Spacer(Modifier.height(SCROLL_BOTTOM_PADDING))
                    }
                }
            }

            // Static curtains: pinned to the viewport's own top/bottom, never to the scroll offset or
            // the ime/nav inset the buttons use, so they can't visibly drift relative to either.
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(TOP_EDGE_FADE_HEIGHT)
                    .background(topCurtain(scheme.background)),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    // ime-aware only, not nav-bar-aware: with the keyboard open this needs to sit
                    // right above it, same as the button row. But padding for BOTH insets (as the
                    // button row does) left a gap between the curtain's bottom edge and the true
                    // screen edge whenever the keyboard was closed — nothing painted there, reading as
                    // a hard seam instead of a soft edge. Painting through the nav-bar region (like the
                    // page background already does) has no such gap.
                    .imePadding()
                    .height(BOTTOM_EDGE_FADE_HEIGHT)
                    .background(bottomCurtain(scheme.background)),
            )

            val sendContainerColor by animateColorAsState(
                targetValue = if (sendEnabled) scheme.primary else scheme.surfaceContainerHigh,
                animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                label = "send-container-color",
            )
            val sendContentColor by animateColorAsState(
                targetValue = if (sendEnabled) scheme.onPrimary else scheme.onSurfaceVariant,
                animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                label = "send-content-color",
            )
            val cancelInteractionSource = remember { MutableInteractionSource() }
            val sendInteractionSource = remember { MutableInteractionSource() }

            val cancelPressed by cancelInteractionSource.collectIsPressedAsState()
            val sendPressed by sendInteractionSource.collectIsPressedAsState()

            val cancelTargetWidth = when {
                cancelPressed -> SEND_BUTTON_HEIGHT + SQUISH_AMOUNT
                sendPressed -> SEND_BUTTON_HEIGHT - SQUISH_AMOUNT
                else -> SEND_BUTTON_HEIGHT
            }

            val cancelWidth by animateDpAsState(
                targetValue = cancelTargetWidth,
                animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                label = "cancel-width"
            )

            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                    .padding(horizontal = Spacing.xxl, vertical = SEND_BUTTON_BOTTOM_PADDING),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(cancelWidth)
                        .height(SEND_BUTTON_HEIGHT)
                        .clip(Radius.full)
                        .background(scheme.surfaceContainerHigh)
                        .clickable(interactionSource = cancelInteractionSource, indication = ripple()) {
                            haptics.reject()
                            cancel()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Symbol(
                        symbol = MaterialSymbol.Close,
                        contentDescription = "Cancel",
                        tint = scheme.onSurface,
                        size = CANCEL_ICON_SIZE,
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(SEND_BUTTON_HEIGHT)
                        .clip(Radius.full)
                        .background(sendContainerColor)
                        .clickable(
                            enabled = sendEnabled,
                            interactionSource = sendInteractionSource,
                            indication = ripple(),
                        ) {
                            haptics.confirm()
                            onSend()
                        },
                ) {
                    val sendLabelStyle = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Light)
                    Row(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(horizontal = Spacing.xl),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = SEND_LABEL,
                            style = sendLabelStyle,
                            color = sendContentColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            // fill = false ensures the text only takes up the space it needs,
                            // keeping the entire Row tight and centered as one lockup.
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(Modifier.width(Spacing.sm))
                        Symbol(
                            symbol = MaterialSymbol.ArrowForward,
                            contentDescription = null,
                            tint = sendContentColor,
                            size = SEND_ICON_SIZE,
                            weight = SEND_ICON_WEIGHT,
                        )
                    }
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

/** Opaque-to-transparent curtain for the top edge — content is fully covered for [STRONG_FADE_HOLD]
 *  of the band nearest the true edge, then ramps away over the rest. A plain overlay (painted after,
 *  i.e. on top of, the scrolling content) rather than an alpha-erase mask on the content itself: since
 *  the scroll area now spans the full viewport, the erase-mask version would need to track exactly
 *  where the content's own edge is, while a static overlay just sits at a fixed screen position. */
private fun topCurtain(background: Color): Brush = Brush.verticalGradient(
    0f to background,
    STRONG_FADE_HOLD to background.copy(0.75f),
    1f to background.copy(alpha = 0f),
)

/** Mirror of [topCurtain] for the bottom edge — transparent near the content, ramping to opaque
 *  toward the true bottom edge where the floating buttons sit. */
private fun bottomCurtain(background: Color): Brush = Brush.verticalGradient(
    0f to background.copy(alpha = 0f),
    (1f - STRONG_FADE_HOLD) to background.copy(alpha = 0.75f),
    1f to background,
)

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
