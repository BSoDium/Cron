package fr.bsodium.cron.ui.screens.memory.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import fr.bsodium.cron.ui.components.rememberCronHaptics
import fr.bsodium.cron.ui.theme.CronColors
import fr.bsodium.cron.ui.theme.CronTheme
import fr.bsodium.cron.ui.theme.MaterialSymbol
import fr.bsodium.cron.ui.theme.Radius
import fr.bsodium.cron.ui.theme.Spacing
import fr.bsodium.cron.ui.theme.Symbol

private val FAB_SIZE = 56.dp
private val FAB_ICON_SIZE = 24.dp
private val SEND_BUTTON_SIZE = 40.dp
private val SEND_ICON_SIZE = 20.dp

/** Collapsed: a plain FAB above the nav pill. Tapping it morphs the same node into a full-width
 *  message-style input docked above the keyboard, with [MemoryScreen]'s scrim dimming the list
 *  behind it — same [AnimatedContent] + [SizeTransform] shape-morph idiom [PrimaryActionFab] already
 *  uses for its working/idle swap, just spanning a bigger size delta. Collapses back to the FAB
 *  automatically once the keyboard is dismissed (tracked via [WindowInsets.isImeVisible], not a
 *  fixed delay — see CLAUDE.md's rule against `delay()`-as-completion-signal).
 *  Built on [BasicTextField] rather than [androidx.compose.material3.TextField] so the pill's own
 *  padding is the only padding in play — Material's TextField bakes in its own ~16dp content inset
 *  on top of whatever the caller adds, pushing the placeholder far past the pill's left edge. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
@Composable
internal fun MemoryComposer(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    enabled: Boolean,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val haptics = rememberCronHaptics()
    val sendEnabled = enabled && value.isNotBlank()
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val imeVisible = WindowInsets.isImeVisible
    var hasShownKeyboard by remember { mutableStateOf(false) }

    LaunchedEffect(expanded) {
        if (expanded) {
            focusRequester.requestFocus()
            keyboardController?.show()
        } else {
            hasShownKeyboard = false
        }
    }
    LaunchedEffect(imeVisible) {
        when {
            imeVisible -> hasShownKeyboard = true
            hasShownKeyboard && expanded -> onExpandedChange(false)
        }
    }

    val sizeSpec = MaterialTheme.motionScheme.fastSpatialSpec<IntSize>()
    val fadeSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()

    AnimatedContent(
        targetState = expanded,
        modifier = modifier,
        transitionSpec = {
            (fadeIn(fadeSpec) togetherWith fadeOut(fadeSpec))
                .using(SizeTransform(clip = false) { _, _ -> sizeSpec })
        },
        contentAlignment = Alignment.BottomEnd,
        label = "memory-composer-morph",
    ) { isExpanded ->
        if (isExpanded) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md),
                color = CronColors.elementSurface,
                shape = Radius.full,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
            ) {
                Row(
                    modifier = Modifier.padding(Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = Spacing.sm, end = Spacing.sm),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (value.isEmpty()) {
                            Text(
                                text = "Tell Cron something to remember",
                                style = MaterialTheme.typography.bodyLarge,
                                color = scheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        BasicTextField(
                            value = value,
                            onValueChange = onValueChange,
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester),
                            enabled = enabled,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = scheme.onSurface),
                            singleLine = true,
                            cursorBrush = SolidColor(scheme.primary),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = { if (value.isNotBlank()) onSend() }),
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(SEND_BUTTON_SIZE)
                            .clip(CircleShape)
                            .background(if (sendEnabled) scheme.primary else scheme.surfaceContainerHigh)
                            .clickable(enabled = sendEnabled) {
                                haptics.confirm()
                                onSend()
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Symbol(
                            symbol = MaterialSymbol.ArrowForward,
                            contentDescription = "Send",
                            tint = if (sendEnabled) scheme.onPrimary else scheme.onSurfaceVariant,
                            size = SEND_ICON_SIZE,
                        )
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .padding(end = Spacing.md)
                    .size(FAB_SIZE)
                    .clip(CircleShape)
                    .background(scheme.primary)
                    .clickable {
                        haptics.confirm()
                        onExpandedChange(true)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Symbol(
                    symbol = MaterialSymbol.AutoAwesome,
                    contentDescription = "Tell Cron something to remember",
                    tint = scheme.onPrimary,
                    size = FAB_ICON_SIZE,
                )
            }
        }
    }
}

@Preview(showBackground = true, name = "Memory composer — collapsed")
@Composable
private fun MemoryComposerCollapsedPreview() {
    CronTheme {
        MemoryComposer(value = "", onValueChange = {}, onSend = {}, enabled = true, expanded = false, onExpandedChange = {})
    }
}

@Preview(showBackground = true, name = "Memory composer — expanded")
@Composable
private fun MemoryComposerExpandedPreview() {
    var value by remember { mutableStateOf("wake me earlier on Fridays") }
    CronTheme {
        MemoryComposer(value = value, onValueChange = { value = it }, onSend = {}, enabled = true, expanded = true, onExpandedChange = {})
    }
}

@Preview(showBackground = true, name = "Memory composer — pending")
@Composable
private fun MemoryComposerPendingPreview() {
    CronTheme {
        MemoryComposer(
            value = "wake me earlier on Fridays",
            onValueChange = {},
            onSend = {},
            enabled = false,
            expanded = true,
            onExpandedChange = {},
        )
    }
}
