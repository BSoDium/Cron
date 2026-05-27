package fr.bsodium.cron.ui.screens.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

/**
 * Two-line greeting header. Surrounding text renders in a thin weight so the
 * bolded user name pops; the avatar slot only appears when a signed-in photo
 * URL is available.
 *
 *   Good morning, **Elliot**
 *   Welcome back
 */
@Composable
fun GreetingHeader(
    prefix: String,
    name: String?,
    modifier: Modifier = Modifier,
    photoUrl: String? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = buildAnnotatedString {
                    append(prefix)
                    if (!name.isNullOrBlank()) {
                        append(", ")
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(name)
                        }
                    }
                },
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Light),
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "Welcome back",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Light),
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        if (!photoUrl.isNullOrBlank()) {
            Avatar(photoUrl = photoUrl)
        }
    }
}

@Composable
private fun Avatar(photoUrl: String) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .border(width = 3.dp, color = MaterialTheme.colorScheme.primary, shape = CircleShape)
            .padding(4.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape)
            .clip(CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = photoUrl,
            contentDescription = "Profile photo",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
