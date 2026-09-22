package fr.bsodium.cron.ui.theme

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable

/**
 * Wraps `@Preview` content in [CronTheme] plus a [Surface] painted with [CronColors.pageBackground] —
 * the same color MainActivity's root `Scaffold` uses (`containerColor = CronColors.pageBackground`).
 * Bare `CronTheme { ... }` previews render on M3's default `colorScheme.background`, which is a
 * different, lighter shade than the real page background; elements that only contrast against the
 * true page surface (see `docs/preview-quirks.md`) go invisible in preview while showing fine on
 * device. Use this instead of `CronTheme` in every `@Preview` function.
 */
@Composable
fun CronPreview(content: @Composable () -> Unit) {
    CronTheme {
        Surface(color = CronColors.pageBackground, content = content)
    }
}
