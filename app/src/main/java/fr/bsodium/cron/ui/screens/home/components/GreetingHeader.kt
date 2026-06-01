package fr.bsodium.cron.ui.screens.home.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import fr.bsodium.cron.ui.theme.CronTypography

/**
 * One-line time-of-day greeting with a bolded user name.
 *
 *   Good morning, **Elliot**
 */
@Composable
fun GreetingHeader(
    prefix: String,
    name: String?,
    modifier: Modifier = Modifier,
) {
    Text(
        modifier = modifier.fillMaxWidth(),
        text = buildAnnotatedString {
            append(prefix)
            if (!name.isNullOrBlank()) {
                append(", ")
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(name)
                }
            }
        },
        style = CronTypography.pageTitle,
        color = MaterialTheme.colorScheme.onBackground,
    )
}
