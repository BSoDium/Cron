package fr.bsodium.cron.ui.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
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
            )
        },
        modifier = modifier,
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Symbol(
                        symbol = MaterialSymbol.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground,
                        autoMirror = true,
                    )
                }
            }
        },
        titleHorizontalAlignment = Alignment.Start,
        colors = TopAppBarDefaults.topAppBarColors(
            // Expanded blends into the edge-to-edge page; collapsed reads as a flat surface bar
            // (a colour shade, never a shadow — the design is flat).
            containerColor = Color.Transparent,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        scrollBehavior = scrollBehavior,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Preview(showBackground = true, widthDp = 412, heightDp = 300)
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
@Preview(showBackground = true, widthDp = 412, heightDp = 300)
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
