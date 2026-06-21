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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import fr.bsodium.cron.ui.components.PageAppBar
import fr.bsodium.cron.ui.components.PredictiveBackCard
import fr.bsodium.cron.ui.screens.settings.LocalSettingsListState
import fr.bsodium.cron.ui.screens.settings.SettingsScreen
import fr.bsodium.cron.ui.theme.CronColors
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
    verticalSpacing: Dp = Spacing.xxl,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val navBottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    val currentState = LocalSettingsListState.current
    val previewIndex = currentState.firstVisibleItemIndex
    val previewOffset = currentState.firstVisibleItemScrollOffset
    PredictiveBackCard(
        onBack = onBack,
        modifier = modifier,
        parentContent = {
            val previewState = remember { LazyListState(previewIndex, previewOffset) }
            SettingsScreen(onOpenCategory = {}, listState = previewState)
        },
    ) { animatedBack ->
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            containerColor = CronColors.pageBackground,
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
                verticalArrangement = Arrangement.spacedBy(verticalSpacing),
            ) {
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = Spacing.lg),
                    )
                }
                content()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Preview(showBackground = true, widthDp = 480, heightDp = 300, fontScale = 1.0f, name = "SettingsDetailScaffold — no description")
@Composable
private fun SettingsDetailScaffoldNoDescriptionPreview() {
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Preview(showBackground = true, widthDp = 480, heightDp = 300, fontScale = 1.0f, name = "SettingsDetailScaffold — with description")
@Composable
private fun SettingsDetailScaffoldWithDescriptionPreview() {
    CronTheme {
        SettingsDetailScaffold(title = "Assistant", subtitle = "Subtle feedback while writing an answer", onBack = {}) {
            SwitchRow(
                title = "Haptic feedback",
                subtitle = "Subtle ticks while the assistant writes",
                checked = true,
                onCheckedChange = {},
            )
        }
    }
}
