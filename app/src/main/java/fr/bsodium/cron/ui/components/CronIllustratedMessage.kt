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
import fr.bsodium.cron.ui.theme.TightTextStyle

private val DefaultIllustrationSize = 200.dp

// Standardized text-width-to-illustration-width ratio, so the copy always reads as tailored to
// the art above it rather than stretching full-width — tune this one value, every call site follows.
// Floored at MinTextWidth so a small illustration (WelcomeStep's 120dp) doesn't force a long
// subtitle into a narrow, ransom-note wrap.
private const val TEXT_WIDTH_RATIO = 1.2f
private val MinTextWidth = 220.dp

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
    textMaxWidth: Dp = (illustrationSize * TEXT_WIDTH_RATIO).coerceAtLeast(MinTextWidth),
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
            // Merge tight leading so the illustration/title gap is only Spacing.xxl, not that
            // plus the font's own invisible top padding — see TightTextStyle's KDoc.
            style = CronTypography.illustratedTitle.merge(TightTextStyle),
            color = titleColor,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = textMaxWidth),
        )
        Spacer(Modifier.height(Spacing.md))
        Text(
            text = subtitle,
            // Same here: without this, the subtitle's bottom leading makes the gap below it read
            // wider than the (font-padding-free) gap above the illustration, even at bias 0.
            style = subtitleStyle.merge(TightTextStyle),
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
                modifier = Modifier.padding(Spacing.xxl),
            )
        }
    }
}
