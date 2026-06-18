package fr.bsodium.cron.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import fr.bsodium.cron.debug.MockApiPrefs
import fr.bsodium.cron.ui.theme.Spacing

private class AboveAnchorPositionProvider(private val gap: Int) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val x = anchorBounds.right - popupContentSize.width
        val y = (anchorBounds.top - popupContentSize.height - gap).coerceAtLeast(0)
        return IntOffset(x, y)
    }
}

/** DEBUG variant — returns a [FabChevronSlot] wired to [MockApiPrefs] for the split FAB. */
@Composable
fun rememberFabChevron(): FabChevronSlot? {
    val context = LocalContext.current
    val prefs = remember { MockApiPrefs(context) }
    if (!prefs.isEnabled) return null
    val isMock = remember { mutableStateOf(prefs.isMockActive) }
    isMock.value = prefs.isMockActive
    val showMenu = remember { mutableStateOf(false) }
    return remember(prefs) {
        FabChevronSlot(
            isMockActiveState = isMock,
            isExpandedState = showMenu,
            onExpandedChange = { showMenu.value = it },
            menuContent = @Composable {
                val gap = with(LocalDensity.current) { Spacing.xs.roundToPx() }
                if (showMenu.value) {
                    Popup(
                        onDismissRequest = { showMenu.value = false },
                        popupPositionProvider = remember(gap) { AboveAnchorPositionProvider(gap) },
                        properties = PopupProperties(focusable = true),
                    ) {
                        Surface(
                            shape = MaterialTheme.shapes.extraSmall,
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            tonalElevation = 3.dp,
                            shadowElevation = 3.dp,
                        ) {
                            Column(
                                Modifier
                                    .width(IntrinsicSize.Max)
                                    .widthIn(min = 166.dp)
                                    .padding(vertical = 8.dp),
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text("Real run", style = MaterialTheme.typography.bodyMedium)
                                            Text(
                                                "Connect to the Anthropic API",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    },
                                    modifier = Modifier.background(
                                        if (!isMock.value) MaterialTheme.colorScheme.secondaryContainer
                                        else Color.Transparent,
                                    ),
                                    onClick = {
                                        prefs.isMockActive = false
                                        isMock.value = false
                                        showMenu.value = false
                                    },
                                )
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text("Mock run", style = MaterialTheme.typography.bodyMedium)
                                            Text(
                                                "Simulate API responses locally",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    },
                                    modifier = Modifier.background(
                                        if (isMock.value) MaterialTheme.colorScheme.secondaryContainer
                                        else Color.Transparent,
                                    ),
                                    onClick = {
                                        prefs.isMockActive = true
                                        isMock.value = true
                                        showMenu.value = false
                                    },
                                )
                            }
                        }
                    }
                }
            },
        )
    }
}
