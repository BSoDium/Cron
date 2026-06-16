package fr.bsodium.cron.ui.screens.settings.categories

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import fr.bsodium.cron.debug.MockApiPrefs
import fr.bsodium.cron.debug.MockScenario
import fr.bsodium.cron.ui.components.SectionLabel
import fr.bsodium.cron.ui.screens.settings.components.CheckboxRow
import fr.bsodium.cron.ui.screens.settings.components.SwitchRow
import fr.bsodium.cron.ui.theme.Spacing

/** DEBUG-ONLY. Toggle + scenario picker for [fr.bsodium.cron.debug.FakeAnthropicClient]. */
@Composable
internal fun DebugSettingsSection() {
    val context = LocalContext.current
    val prefs = remember { MockApiPrefs(context) }
    var isEnabled by remember { mutableStateOf(prefs.isEnabled) }
    var scenario by remember { mutableStateOf(prefs.scenario) }

    HorizontalDivider()
    Spacer(Modifier.height(Spacing.xs))
    SectionLabel("Debug")
    SwitchRow(
        title = "Use mock API",
        subtitle = "Replay scripted responses — no credits consumed",
        checked = isEnabled,
        onCheckedChange = { v -> prefs.isEnabled = v; isEnabled = v },
    )
    AnimatedVisibility(visible = isEnabled) {
        Column {
            MockScenario.entries.forEach { s ->
                CheckboxRow(
                    title = s.label,
                    subtitle = s.description,
                    checked = scenario == s,
                    onCheckedChange = { if (it) { prefs.scenario = s; scenario = s } },
                )
            }
        }
    }
}
