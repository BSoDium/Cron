package fr.bsodium.cron.ui.screens.home.components

import android.content.res.Configuration
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.bsodium.cron.R
import fr.bsodium.cron.alarm.nextEveningPlanInstant
import fr.bsodium.cron.ui.components.recolored
import fr.bsodium.cron.ui.theme.CronTheme
import fr.bsodium.cron.ui.theme.CronTypography
import fr.bsodium.cron.ui.theme.Spacing
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.util.Locale

/**
 * The resting state shown once the current alarm is done (passed or dismissed) and no plan exists for
 * the next one yet: a themed illustration, a quiet note that no plan has run, plus when the nightly run
 * will fire. The play FAB remains the manual-run CTA. (First run shows [OnboardingHint] instead.)
 */
@Composable
internal fun NextPlanHint(
    autoAlarmsEnabled: Boolean,
    eveningTriggerTime: LocalTime,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        NoPlanIllustration(Modifier.size(180.dp))
        Spacer(Modifier.height(Spacing.md))
        Text(
            text = "No plan available yet",
            style = CronTypography.bodySerif.copy(fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 30.sp),
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Spacing.sm))
        Text(
            text = nextPlanSubline(autoAlarmsEnabled, eveningTriggerTime),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun nextPlanSubline(autoAlarmsEnabled: Boolean, eveningTriggerTime: LocalTime): String {
    if (!autoAlarmsEnabled) return "No plan available for your next alarm. Automatic planning is off — run one yourself."
    val tz = TimeZone.currentSystemDefault()
    // Tick each minute so "tonight" rolls to "tomorrow" once the trigger time passes.
    val now by produceState(Clock.System.now(), eveningTriggerTime) {
        while (true) {
            value = Clock.System.now()
            delay(60_000)
        }
    }
    val nextDate = nextEveningPlanInstant(eveningTriggerTime, now, tz).toLocalDateTime(tz).date
    val today = now.toLocalDateTime(tz).date
    val whenWord = if (nextDate == today) "tonight" else "tomorrow"
    // Locale.US for the clock readout (ASCII digits) per the LCD/clock formatting rule.
    val hhmm = String.format(Locale.US, "%02d:%02d", eveningTriggerTime.hour, eveningTriggerTime.minute)
    return "No plan available for your next alarm. The next plan will run $whenWord at $hhmm."
}

/**
 * The empty-state clock, its fixed palette retinted onto Material You across three accent hues so it tracks
 * the wallpaper instead of reading monochrome: the dial → `primary`, the background "speed wings" →
 * `tertiary` (their lighter overlay → `tertiaryContainer`), the bells + feet → `secondary`; the numbers,
 * hands, ticks and shading → `onSurface` (line-art legible in light & dark); the dial face/sheen → `surface`
 * and the ground shadow → `surfaceVariant`. `recolored` only swaps colors, preserving every path's alpha.
 */
@Composable
private fun NoPlanIllustration(modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val source = ImageVector.vectorResource(R.drawable.ic_no_plan_illustration)
    val illustration = remember(
        source,
        scheme.onSurface, scheme.primary, scheme.secondary, scheme.tertiary,
        scheme.tertiaryContainer, scheme.surface, scheme.surfaceVariant,
    ) {
        source.recolored { original ->
            when (original) {
                NO_PLAN_INK, NO_PLAN_BLACK -> scheme.onSurface
                NO_PLAN_PRIMARY -> scheme.primary
                NO_PLAN_SECONDARY -> scheme.secondary
                NO_PLAN_TERTIARY -> scheme.tertiary
                NO_PLAN_TERTIARY_LIGHT -> scheme.tertiaryContainer
                NO_PLAN_PAPER -> scheme.surface
                NO_PLAN_GROUND -> scheme.surfaceVariant
                else -> original
            }
        }
    }
    Image(imageVector = illustration, contentDescription = null, modifier = modifier)
}

/** Source fills of `ic_no_plan_illustration` (sentinel hues assigned per region), remapped onto
 *  `colorScheme` (see [recolored]). */
private val NO_PLAN_INK = Color(0xFF263238)
private val NO_PLAN_BLACK = Color(0xFF000000)
private val NO_PLAN_PRIMARY = Color(0xFF407BFF)
private val NO_PLAN_SECONDARY = Color(0xFFEE3377)
private val NO_PLAN_TERTIARY = Color(0xFF11AA99)
private val NO_PLAN_TERTIARY_LIGHT = Color(0xFF66E0D0)
private val NO_PLAN_PAPER = Color(0xFFFFFFFF)
private val NO_PLAN_GROUND = Color(0xFFF5F5F5)

@Preview(showBackground = true, name = "No-plan illustration — light")
@Preview(showBackground = true, name = "No-plan illustration — dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun NoPlanIllustrationPreview() {
    CronTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            NoPlanIllustration(Modifier.padding(Spacing.xl).size(220.dp))
        }
    }
}

@Preview(showBackground = true, name = "Next plan — auto on")
@Composable
private fun NextPlanHintAutoOnPreview() {
    CronTheme {
        NextPlanHint(autoAlarmsEnabled = true, eveningTriggerTime = LocalTime(20, 0), modifier = Modifier.padding(Spacing.xl))
    }
}

@Preview(showBackground = true, name = "Next plan — auto off")
@Composable
private fun NextPlanHintAutoOffPreview() {
    CronTheme {
        NextPlanHint(autoAlarmsEnabled = false, eveningTriggerTime = LocalTime(20, 0), modifier = Modifier.padding(Spacing.xl))
    }
}
