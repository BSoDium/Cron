package fr.bsodium.cron.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.bsodium.cron.ui.theme.CronTheme
import fr.bsodium.cron.ui.theme.CronTypography
import fr.bsodium.cron.ui.theme.Spacing

private val DefaultIllustrationSize = 200.dp

/**
 * Illustration, serif heading, supporting line — the shared layout behind empty states and
 * onboarding steps (Memory's "no entries yet", the home screen's onboarding/next-plan hints,
 * onboarding's welcome/done steps). [content] renders below the text, e.g. a CTA button.
 */
@Composable
fun CronIllustratedMessage(
    type: CronIllustrationType,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    illustrationSize: Dp = DefaultIllustrationSize,
    textMaxWidth: Dp = Dp.Unspecified,
    subtitleStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    titleColor: Color = MaterialTheme.colorScheme.onBackground,
    subtitleColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    content: @Composable () -> Unit = {},
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CronIllustration(type = type, modifier = Modifier.size(illustrationSize))
        Spacer(Modifier.height(Spacing.xxl))
        Text(
            text = title,
            style = CronTypography.illustratedTitle,
            color = titleColor,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = textMaxWidth),
        )
        Spacer(Modifier.height(Spacing.md))
        Text(
            text = subtitle,
            style = subtitleStyle,
            color = subtitleColor,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = textMaxWidth),
        )
        content()
    }
}

@Preview(name = "Illustrated message — light", showBackground = true)
@Preview(name = "Illustrated message — dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun CronIllustratedMessagePreview() {
    CronTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            CronIllustratedMessage(
                type = CronIllustrationType.Flowers1,
                title = "Your memories are empty",
                subtitle = "Tell Cron something to remember and it will appear here.",
                textMaxWidth = 240.dp,
                modifier = Modifier.padding(Spacing.xxl),
            )
        }
    }
}
