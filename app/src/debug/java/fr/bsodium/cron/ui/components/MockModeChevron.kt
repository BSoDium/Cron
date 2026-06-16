package fr.bsodium.cron.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import fr.bsodium.cron.debug.MockApiPrefs
import fr.bsodium.cron.ui.theme.MaterialSymbol
import fr.bsodium.cron.ui.theme.Radius
import fr.bsodium.cron.ui.theme.Spacing
import fr.bsodium.cron.ui.theme.Symbol

/** DEBUG variant — returns a [FabChevronSlot] wired to [MockApiPrefs] for the split FAB. */
@Composable
fun rememberFabChevron(): FabChevronSlot? {
    val context = LocalContext.current
    val prefs = remember { MockApiPrefs(context) }
    val isMock = remember { mutableStateOf(prefs.isEnabled) }
    val showMenu = remember { mutableStateOf(false) }
    return remember(prefs) {
        FabChevronSlot(
            isMockActiveState = isMock,
            isExpandedState = showMenu,
            onExpandedChange = { showMenu.value = it },
            menuContent = @Composable {
                DropdownMenu(
                    expanded = showMenu.value,
                    onDismissRequest = { showMenu.value = false },
                    modifier = Modifier.widthIn(min = 162.dp),
                    offset = DpOffset(0.dp, Spacing.sm),
                ) {
                    DropdownMenuItem(
                        text = { Text("Real run", style = MaterialTheme.typography.bodyMedium) },
                        leadingIcon = { Symbol(MaterialSymbol.Cloud, contentDescription = null) },
                        modifier = if (!isMock.value) Modifier.background(
                            MaterialTheme.colorScheme.secondaryContainer,
                            RoundedCornerShape(Radius.sm),
                        ) else Modifier,
                        onClick = {
                            prefs.isEnabled = false
                            isMock.value = false
                            showMenu.value = false
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Mock run", style = MaterialTheme.typography.bodyMedium) },
                        leadingIcon = { Symbol(MaterialSymbol.Science, contentDescription = null) },
                        modifier = if (isMock.value) Modifier.background(
                            MaterialTheme.colorScheme.secondaryContainer,
                            RoundedCornerShape(Radius.sm),
                        ) else Modifier,
                        onClick = {
                            prefs.isEnabled = true
                            isMock.value = true
                            showMenu.value = false
                        },
                    )
                }
            },
        )
    }
}
