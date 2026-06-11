package fr.bsodium.cron.ui.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Wraps an icon-only button so a long-press (or hover) surfaces a plain tooltip with [label] — the visible
 * affordance icon buttons otherwise lack. (Screen readers already get the label via the button's
 * `contentDescription`; this is the sighted-user counterpart.) Centralises the experimental-API opt-in.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IconTooltip(
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
        modifier = modifier,
        content = content,
    )
}
