package fr.bsodium.cron.ui.components

import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
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
    val barContainer = MaterialTheme.colorScheme.surfaceContainerLow
    LargeFlexibleTopAppBar(
        title = {
            // Brand face at Medium — between the theme's SemiBold headline default (too heavy here) and
            // the old Light page title; the bar's size + large→small interpolation are left intact.
            Text(
                text = title,
                style = if (LocalInspectionMode.current) {
                    // See docs/preview-quirks.md — Layoutlib clips the last glyph when letterSpacing < 0.
                    LocalTextStyle.current.copy(
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Normal,
                        letterSpacing = 0.sp,
                    )
                } else {
                    LocalTextStyle.current.copy(
                        fontFamily = CronTypography.pageTitle.fontFamily,
                        fontWeight = FontWeight.Normal,
                    )
                },
                softWrap = false,
                maxLines = 1,
                overflow = TextOverflow.Visible,
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
            // Same for both: suppresses M3's snapping container transition; we drive the fade via barContainer.
            containerColor = barContainer,
            scrolledContainerColor = barContainer,
        ),
        // See docs/preview-quirks.md — Layoutlib inflates status-bar inset, pushing content off canvas.
        windowInsets = if (LocalInspectionMode.current) WindowInsets(0) else TopAppBarDefaults.windowInsets,
        scrollBehavior = scrollBehavior,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Preview(showBackground = true, widthDp = 480, heightDp = 300, fontScale = 1.0f)
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
@Preview(showBackground = true, widthDp = 480, heightDp = 300, fontScale = 1.0f)
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

