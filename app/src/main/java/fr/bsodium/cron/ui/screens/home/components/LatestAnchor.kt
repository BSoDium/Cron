package fr.bsodium.cron.ui.screens.home.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.bsodium.cron.ui.theme.CronTheme
import fr.bsodium.cron.ui.theme.MaterialSymbol
import fr.bsodium.cron.ui.theme.Symbol

private val LATEST_GLYPH_SIZE = 18.dp

/** The single latest AI run's foreground: just the glyph. The morphing shape it sits on is filled
 *  by [TimelineTrackOverlay]'s socket carve (a shared `Morph`/progress hoisted in [TimelineNode]) —
 *  track-matched via `trackAccentColor` (Round 27) rather than a flat `primaryContainer` — so the
 *  carve and the surface are one thing that can't drift — see docs/color-roles.md Round 14. [isAsleep]
 *  picks the paired `trackOnAccentColor` for this glyph so it stays correctly paired with whichever
 *  fill the overlay actually painted underneath it. The morph fills [FLUSH_ANCHOR_SIZE] (Latest is
 *  always a cap) like every other cap anchor; only this glyph stays its own size. */
@Composable
internal fun LatestAnchor(symbol: MaterialSymbol, isAsleep: Boolean, modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(FLUSH_ANCHOR_SIZE), contentAlignment = Alignment.Center) {
        Symbol(
            symbol = symbol,
            contentDescription = null,
            tint = trackOnAccentColor(isAsleep),
            size = LATEST_GLYPH_SIZE,
        )
    }
}

@Preview(showBackground = true, name = "Latest anchor — glyph")
@Composable
private fun LatestAnchorPreview() {
    CronTheme {
        LatestAnchor(symbol = MaterialSymbol.Schedule, isAsleep = false)
    }
}
