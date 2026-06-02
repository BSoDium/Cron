package fr.bsodium.cron.ui.screens.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import fr.bsodium.cron.ui.components.SectionLabel
import fr.bsodium.cron.ui.theme.Spacing

/** Labelled settings group: a spaced header followed by its rows. */
@Composable
internal fun Section(
    label: String,
    content: @Composable () -> Unit,
) {
    Spacer(modifier = Modifier.height(Spacing.xxl + Spacing.xs))
    SectionLabel(text = label)
    Spacer(modifier = Modifier.height(Spacing.md + Spacing.xxs))
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xl)) {
        content()
    }
}
