package fr.bsodium.cron.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import fr.bsodium.cron.ui.theme.Radius
import fr.bsodium.cron.ui.theme.Spacing
import java.util.Locale

private val PILL_VPAD = 5.dp
private val DOT_SIZE = 7.dp

/**
 * Uppercase, tracked-out, muted label used as the only "header"
 * inside spacing-grouped sections.
 */
@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text.uppercase(Locale.ROOT),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/**
 * Single source of truth for the rounded-pill badge pattern.
 * Accepts an optional leading colored dot for status indication.
 */
@Composable
fun PillBadge(
    text: String,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    leadingDot: Color? = null,
    textStyle: TextStyle? = null,
) {
    Surface(
        color = containerColor,
        shape = Radius.full,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = PILL_VPAD),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs + Spacing.xxs),
        ) {
            if (leadingDot != null) {
                Box(
                    modifier = Modifier
                        .size(DOT_SIZE)
                        .background(leadingDot, CircleShape),
                )
            }
            Text(
                text = text,
                style = textStyle ?: MaterialTheme.typography.labelSmall,
                color = contentColor,
            )
        }
    }
}
