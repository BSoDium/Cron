package fr.bsodium.cron.ui.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import fr.bsodium.cron.ui.theme.CronTheme
import fr.bsodium.cron.ui.theme.CronTypography
import fr.bsodium.cron.ui.theme.MaterialSymbol
import fr.bsodium.cron.ui.theme.Symbol

/**
 * Material 3 Expressive large flexible app bar for the tab and detail pages. The title keeps the brand
 * [CronTypography.pageTitle] face/weight while inheriting the bar's interpolated size and colour, so it
 * reads large when expanded and cross-fades to a small left title as content scrolls under it. Pass
 * [onBack] on drill-down screens to surface a back affordance; tabs leave it null (no leading icon).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PageAppBar(
    title: String,
    scrollBehavior: TopAppBarScrollBehavior,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
) {
    // Fade the bar's surface shade in *in step with* the title's big→small collapse. Lerp between two
    // OPAQUE colours — page background → surfaceContainer — never `Color.Transparent`: transparent is
    // transparent *black*, so a mid-fade value composites as a near-black blip over the page (and M3's
    // cross-faded layers stack it), reading as an elevation overshoot — the "weird gradient". Opaque
    // endpoints ramp monotonically (very-low → slight elevation), matching the default app-bar feel.
    val barContainer = lerp(
        MaterialTheme.colorScheme.background,
        MaterialTheme.colorScheme.surfaceContainer,
        scrollBehavior.state.collapsedFraction,
    )
    LargeFlexibleTopAppBar(
        title = {
            // Brand face at Medium — between the theme's SemiBold headline default (too heavy here) and
            // the old Light page title; the bar's size + large→small interpolation are left intact.
            Text(
                text = title,
                style = LocalTextStyle.current.copy(
                    fontFamily = CronTypography.pageTitle.fontFamily,
                    fontWeight = FontWeight.Normal,
                ),
                softWrap = false,
            )
        },
        modifier = modifier,
        navigationIcon = {
            if (onBack != null) {
                IconTooltip("Back") {
                    FilledTonalIconButton(
                        onClick = onBack,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                    ) {
                        Symbol(
                            symbol = MaterialSymbol.ArrowBack,
                            contentDescription = "Back",
                            autoMirror = true,
                        )
                    }
                }
            }
        },
        titleHorizontalAlignment = Alignment.Start,
        colors = TopAppBarDefaults.topAppBarColors(
            // Same colour for both so M3 doesn't run its own (snapping) container transition — we drive the
            // shade ourselves via [barContainer]: transparent expanded → surfaceContainer collapsed, synced
            // to the title. A colour shade, never a shadow (the design is flat).
            containerColor = barContainer,
            scrolledContainerColor = barContainer,
        ),
        scrollBehavior = scrollBehavior,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Preview(showBackground = true, widthDp = 480, heightDp = 300)
@Composable
private fun PageAppBarPreview() {
    CronTheme {
        val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = { PageAppBar(title = "Settings", scrollBehavior = scrollBehavior) },
        ) { inner ->
            Text("body", modifier = Modifier.padding(inner))
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Preview(showBackground = true, widthDp = 480, heightDp = 300)
@Composable
private fun PageAppBarWithBackPreview() {
    CronTheme {
        val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = { PageAppBar(title = "Schedule", scrollBehavior = scrollBehavior, onBack = {}) },
        ) { inner ->
            Text("body", modifier = Modifier.padding(inner))
        }
    }
}

