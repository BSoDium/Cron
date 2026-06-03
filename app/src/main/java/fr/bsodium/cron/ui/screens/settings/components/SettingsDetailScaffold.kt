package fr.bsodium.cron.ui.screens.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import fr.bsodium.cron.ui.components.ScreenTitle
import fr.bsodium.cron.ui.theme.CronTheme
import fr.bsodium.cron.ui.theme.Spacing

/**
 * Edge-to-edge container for a Settings category sub-screen: a back affordance above a large
 * [title], then the category's [content] spaced like the old in-section rows. The nav pill is
 * hidden on sub-screens, so the bottom inset only clears the system nav bar.
 */
@Composable
internal fun SettingsDetailScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val navBottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val statusInsetTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.xl)
            .padding(top = statusInsetTop + Spacing.md, bottom = navBottomInset + Spacing.xxxl),
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }
        Spacer(Modifier.height(Spacing.xs))
        ScreenTitle(title)
        Spacer(Modifier.height(Spacing.xl))
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xl)) { content() }
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsDetailScaffoldPreview() {
    CronTheme {
        SettingsDetailScaffold(title = "Assistant", onBack = {}) {
            SwitchRow(
                title = "Haptic feedback",
                subtitle = "Subtle ticks while the assistant writes",
                checked = true,
                onCheckedChange = {},
            )
        }
    }
}
