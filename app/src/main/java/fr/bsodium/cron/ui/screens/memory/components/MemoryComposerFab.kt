package fr.bsodium.cron.ui.screens.memory.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.bsodium.cron.ui.components.rememberCronHaptics
import fr.bsodium.cron.ui.theme.CronTheme
import fr.bsodium.cron.ui.theme.MaterialSymbol
import fr.bsodium.cron.ui.theme.Radius
import fr.bsodium.cron.ui.theme.Spacing
import fr.bsodium.cron.ui.theme.Symbol

private val FAB_SIZE = 56.dp
private val FAB_ICON_SIZE = 24.dp

/** Normal (fixed bar) nav mode's freestanding trigger for [MemoryFullScreenComposer] — the app's
 *  usual Expressive [RoundedCornerShape], matching [fr.bsodium.cron.ui.components.PrimaryActionFab]'s
 *  silhouette. Compact-nav mode doesn't use this: its trigger lives in
 *  [fr.bsodium.cron.ui.components.CronFloatingNav]'s own row instead (see [MemoryScreen]). Fades
 *  out rather than disappearing outright while the full-screen composer is up. */
@Composable
internal fun MemoryComposerFab(visible: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val haptics = rememberCronHaptics()
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
        exit = fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec()),
        modifier = modifier,
        label = "memory-fab-visibility",
    ) {
        Box(
            modifier = Modifier
                .padding(end = Spacing.md)
                .size(FAB_SIZE)
                .clip(RoundedCornerShape(Radius.lg))
                .background(scheme.primary)
                .clickable {
                    haptics.confirm()
                    onClick()
                },
            contentAlignment = Alignment.Center,
        ) {
            Symbol(
                symbol = MaterialSymbol.HistoryEdu,
                contentDescription = "Tell Cron something to remember",
                tint = scheme.onPrimary,
                size = FAB_ICON_SIZE,
            )
        }
    }
}

@Preview(showBackground = true, name = "Memory FAB")
@Composable
private fun MemoryComposerFabPreview() {
    CronTheme {
        MemoryComposerFab(visible = true, onClick = {})
    }
}
