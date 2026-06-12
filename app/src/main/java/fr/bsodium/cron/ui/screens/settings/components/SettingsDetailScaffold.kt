package fr.bsodium.cron.ui.screens.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.tooling.preview.Preview
import fr.bsodium.cron.ui.components.PageAppBar
import fr.bsodium.cron.ui.theme.CronTheme
import fr.bsodium.cron.ui.theme.Spacing

/**
 * Edge-to-edge container for a Settings category sub-screen: a large flexible app bar carrying the
 * [title] and a back affordance, then the category's [content] spaced like the old in-section rows.
 * The nav pill is hidden on sub-screens, so the bottom inset only clears the system nav bar.
 *
 * Wrapped in [PredictiveBackCard] so the back gesture (and the app-bar back arrow, via the wrapped
 * callback) plays the two-phase scale-into-card; the opaque background lets the card read as a solid
 * surface lifted over the dimmed parent. See docs/navigation.md.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SettingsDetailScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val navBottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    PredictiveBackCard(onBack = onBack, modifier = modifier) { animatedBack ->
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets(0),
            topBar = { PageAppBar(title = title, scrollBehavior = scrollBehavior, onBack = animatedBack) },
        ) { inner ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = inner.calculateTopPadding())
                    .verticalScroll(rememberScrollState())
                    .padding(start = Spacing.xl, end = Spacing.xl, top = Spacing.lg)
                    .padding(bottom = navBottomInset + Spacing.xxxl),
                verticalArrangement = Arrangement.spacedBy(Spacing.xxl),
            ) {
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                content()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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
