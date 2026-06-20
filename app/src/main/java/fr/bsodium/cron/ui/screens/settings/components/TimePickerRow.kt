package fr.bsodium.cron.ui.screens.settings.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import fr.bsodium.cron.ui.theme.MaterialSymbol
import fr.bsodium.cron.ui.theme.Radius
import fr.bsodium.cron.ui.theme.Spacing
import fr.bsodium.cron.ui.theme.Symbol
import java.util.Locale
import kotlinx.datetime.LocalTime

/** Clickable row that shows a 24h time and opens a [TimePickerDialog] to edit it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TimePickerRow(
    label: String,
    description: String,
    time: LocalTime,
    onTimeSelected: (LocalTime) -> Unit,
) {
    var showDialog by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showDialog = true },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = String.format(Locale.US, "%02d:%02d", time.hour, time.minute),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
    }

    if (showDialog) {
        TimePickerDialog(
            initial = time,
            onDismiss = { showDialog = false },
            onConfirm = { newTime ->
                onTimeSelected(newTime)
                showDialog = false
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TimePickerDialog(
    initial: LocalTime,
    onDismiss: () -> Unit,
    onConfirm: (LocalTime) -> Unit,
    hardLatest: LocalTime? = null,
    onEditLimit: (() -> Unit)? = null,
) {
    val pickerState = rememberTimePickerState(
        initialHour = initial.hour,
        initialMinute = initial.minute,
        is24Hour = true,
    )
    val overLimit by remember(hardLatest) {
        derivedStateOf {
            hardLatest != null && (pickerState.hour > hardLatest.hour ||
                (pickerState.hour == hardLatest.hour && pickerState.minute > hardLatest.minute))
        }
    }
    val lighterTypography = MaterialTheme.typography.copy(
        displayLarge = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Normal),
    )
    var showDial by remember { mutableStateOf(true) }
    Dialog(onDismissRequest = onDismiss) {
        Column {
            Surface(
                shape = RoundedCornerShape(Radius.xl),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
            ) {
                Column {
                    Text(
                        text = "Select time",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = Spacing.xl, top = Spacing.xl),
                    )
                    Spacer(Modifier.height(Spacing.xl))
                    MaterialTheme(typography = lighterTypography) {
                        if (showDial) {
                            TimePicker(
                                state = pickerState,
                                modifier = Modifier.padding(horizontal = Spacing.xl),
                            )
                        } else {
                            TimeInput(
                                state = pickerState,
                                modifier = Modifier.padding(horizontal = Spacing.xl),
                            )
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = Spacing.md, end = Spacing.sm, bottom = Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = { showDial = !showDial }) {
                            Symbol(
                                symbol = if (showDial) MaterialSymbol.Keyboard else MaterialSymbol.Schedule,
                                contentDescription = if (showDial) "Switch to keyboard" else "Switch to dial",
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = onDismiss) { Text("Cancel") }
                        TextButton(
                            onClick = { onConfirm(LocalTime(pickerState.hour, pickerState.minute)) },
                            enabled = !overLimit,
                        ) {
                            Text("OK")
                        }
                    }
                }
            }
            AnimatedVisibility(
                visible = overLimit,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.sm),
                    shape = RoundedCornerShape(Radius.lg),
                    color = MaterialTheme.colorScheme.errorContainer,
                    tonalElevation = 0.dp,
                ) {
                    Row(
                        modifier = Modifier.padding(
                            start = Spacing.md,
                            top = Spacing.md,
                            bottom = Spacing.md,
                            end = Spacing.sm,
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.error,
                        ) {
                            Symbol(
                                symbol = MaterialSymbol.Schedule,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onError,
                                modifier = Modifier.padding(Spacing.sm),
                            )
                        }
                        Text(
                            text = String.format(
                                Locale.US,
                                "Must be before %02d:%02d",
                                hardLatest!!.hour,
                                hardLatest.minute,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f),
                        )
                        if (onEditLimit != null) {
                            TextButton(onClick = { onEditLimit(); onDismiss() }) {
                                Text("Edit")
                            }
                        }
                    }
                }
            }
        }
    }
}
