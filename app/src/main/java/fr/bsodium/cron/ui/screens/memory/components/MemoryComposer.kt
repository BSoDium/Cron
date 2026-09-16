package fr.bsodium.cron.ui.screens.memory.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.bsodium.cron.ui.theme.CronColors
import fr.bsodium.cron.ui.theme.CronTheme
import fr.bsodium.cron.ui.theme.MaterialSymbol
import fr.bsodium.cron.ui.theme.Radius
import fr.bsodium.cron.ui.theme.Spacing
import fr.bsodium.cron.ui.theme.Symbol

private val SEND_BUTTON_SIZE = 40.dp
private val SEND_ICON_SIZE = 20.dp

/** Bottom-docked message-style input: typing here and sending triggers a memory-mutation turn — the
 *  assistant decides what to add/edit/delete, not a direct form edit (see MemoryScreen's own KDoc).
 *  Built on [BasicTextField] rather than [androidx.compose.material3.TextField] so the pill's own
 *  padding is the only padding in play — Material's TextField bakes in its own ~16dp content inset
 *  on top of whatever the caller adds, pushing the placeholder far past the pill's left edge. */
@Composable
internal fun MemoryComposer(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val sendEnabled = enabled && value.isNotBlank()
    Surface(
        modifier = modifier.fillMaxWidth(),
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
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.fillMaxWidth(),
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
                    .clickable(enabled = sendEnabled, onClick = onSend),
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
}

@Preview(showBackground = true, name = "Memory composer")
@Composable
private fun MemoryComposerPreview() {
    var value by remember { mutableStateOf("") }
    CronTheme {
        MemoryComposer(value = value, onValueChange = { value = it }, onSend = {}, enabled = true)
    }
}

@Preview(showBackground = true, name = "Memory composer — filled")
@Composable
private fun MemoryComposerFilledPreview() {
    CronTheme {
        MemoryComposer(value = "wake me earlier on Fridays", onValueChange = {}, onSend = {}, enabled = true)
    }
}

@Preview(showBackground = true, name = "Memory composer — pending")
@Composable
private fun MemoryComposerPendingPreview() {
    CronTheme {
        MemoryComposer(value = "wake me earlier on Fridays", onValueChange = {}, onSend = {}, enabled = false)
    }
}
