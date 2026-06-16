package fr.bsodium.cron.ui.components

import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import fr.bsodium.cron.debug.MockApiPrefs

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
            onClick = { showMenu.value = true },
            menuContent = @Composable {
                DropdownMenu(
                    expanded = showMenu.value,
                    onDismissRequest = { showMenu.value = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Real run", style = MaterialTheme.typography.bodyMedium) },
                        leadingIcon = { RadioButton(selected = !isMock.value, onClick = null) },
                        onClick = {
                            prefs.isEnabled = false
                            isMock.value = false
                            showMenu.value = false
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Mock run", style = MaterialTheme.typography.bodyMedium) },
                        leadingIcon = { RadioButton(selected = isMock.value, onClick = null) },
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
