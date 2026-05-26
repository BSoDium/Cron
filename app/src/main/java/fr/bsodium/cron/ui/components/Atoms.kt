package fr.bsodium.cron.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

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
        text = text.uppercase(),
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
) {
    Surface(
        color = containerColor,
        shape = RoundedCornerShape(50),
        modifier = modifier,
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
        ) {
            if (leadingDot != null) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .background(leadingDot, CircleShape),
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor,
            )
        }
    }
}

/**
 * Builds an [AnnotatedString] from [template], replacing each `{{key}}`
 * placeholder with the value from [highlights] rendered in the brand
 * accent color and SemiBold weight. Unmatched placeholders are left
 * literal.
 *
 * Example:
 * ```
 * highlightedAnnotated(
 *   "Wake at {{time}} for {{event}}.",
 *   mapOf("time" to "07:30", "event" to "9:00 in Paris"),
 * )
 * ```
 */
@Composable
fun highlightedAnnotated(
    template: String,
    highlights: Map<String, String>,
    accent: Color = MaterialTheme.colorScheme.primary,
): AnnotatedString = buildAnnotatedString {
    val regex = Regex("\\{\\{(\\w+)\\}\\}")
    var cursor = 0
    for (match in regex.findAll(template)) {
        append(template.substring(cursor, match.range.first))
        val key = match.groupValues[1]
        val value = highlights[key]
        if (value != null) {
            withStyle(SpanStyle(color = accent, fontWeight = FontWeight.SemiBold)) {
                append(value)
            }
        } else {
            append(match.value)
        }
        cursor = match.range.last + 1
    }
    if (cursor < template.length) append(template.substring(cursor))
}
