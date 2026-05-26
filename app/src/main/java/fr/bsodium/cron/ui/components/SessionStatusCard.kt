package fr.bsodium.cron.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import fr.bsodium.cron.session.model.ActionType
import fr.bsodium.cron.session.model.SessionStatus
import fr.bsodium.cron.ui.theme.ExpressiveFontFamily
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

data class SessionDisplayState(
    val status: SessionStatus,
    val action: ActionType,
    val alarmTime: LocalTime?,
    val reason: String,
    val sessionDate: LocalDate,
    val snoozeCount: Int,
)

/**
 * Visual anchor of the home screen: the resolved alarm time at hero scale,
 * with a small status row above and a date / relative-time subtitle below.
 *
 * Three states:
 *  - Alarm scheduled → time string (e.g. "09:30") as hero.
 *  - Planning (no time yet) → "Planning…" at hero size, reduced opacity.
 *  - DoNothing / Complete / idle → em-dash "—" hero with a clear status label.
 */
@Composable
fun HomeHeader(
    state: SessionDisplayState?,
    modifier: Modifier = Modifier,
) {
    val heroState = remember(state) { resolveHero(state) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Status row: dot + short status label.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatusDot(state?.status)
            Text(
                text = heroState.statusLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Hero time / placeholder.
        Text(
            text = heroState.heroText,
            style = HeroTimeStyle,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = heroState.heroOpacity),
            modifier = Modifier.fillMaxWidth(),
        )

        // Subtitle: relative-time + date.
        Text(
            text = heroState.subtitle,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val HeroTimeStyle: TextStyle = TextStyle(
    fontFamily = ExpressiveFontFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 96.sp,
    lineHeight = 100.sp,
    letterSpacing = (-0.04).em,
)

@Composable
private fun StatusDot(status: SessionStatus?) {
    val color = when (status) {
        null -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
        SessionStatus.Planning -> MaterialTheme.colorScheme.primary
        SessionStatus.Monitoring, SessionStatus.ReMonitoring -> Color(0xFF4ADE80)
        SessionStatus.Awake -> MaterialTheme.colorScheme.primary
        SessionStatus.Complete -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    }
    Box(
        modifier = Modifier
            .size(10.dp)
            .background(color, CircleShape),
    )
}

private data class HeroSnapshot(
    val statusLabel: String,
    val heroText: String,
    val heroOpacity: Float,
    val subtitle: String,
)

private val DateSubtitleFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, MMM d")

private fun resolveHero(state: SessionDisplayState?): HeroSnapshot {
    val sessionDate = state?.sessionDate?.let { java.time.LocalDate.of(it.year, it.monthNumber, it.dayOfMonth) }
        ?: java.time.LocalDate.now()
    val dateText = sessionDate.format(DateSubtitleFormatter)

    return when {
        state == null -> HeroSnapshot(
            statusLabel = "Idle",
            heroText = "—",
            heroOpacity = 0.45f,
            subtitle = "Open settings to plan tonight's alarm.",
        )
        state.alarmTime != null -> {
            val relative = relativeWhen(state.sessionDate, state.alarmTime)
            HeroSnapshot(
                statusLabel = labelFor(state.status, hasAlarm = true),
                heroText = "%02d:%02d".format(state.alarmTime.hour, state.alarmTime.minute),
                heroOpacity = 1f,
                subtitle = "$relative · $dateText",
            )
        }
        state.status == SessionStatus.Planning -> HeroSnapshot(
            statusLabel = "Planning",
            heroText = "Planning…",
            heroOpacity = 0.6f,
            subtitle = "Reading your calendar and picking a wake time · $dateText",
        )
        else -> HeroSnapshot(
            statusLabel = labelFor(state.status, hasAlarm = false),
            heroText = "—",
            heroOpacity = 0.45f,
            subtitle = "No alarm scheduled · $dateText",
        )
    }
}

private fun labelFor(status: SessionStatus, hasAlarm: Boolean): String = when (status) {
    SessionStatus.Planning -> "Planning"
    SessionStatus.Monitoring -> if (hasAlarm) "Scheduled" else "Monitoring"
    SessionStatus.ReMonitoring -> "Re-monitoring"
    SessionStatus.Awake -> "Awake"
    SessionStatus.Complete -> "Done for today"
}

private fun relativeWhen(sessionDate: LocalDate, alarmTime: LocalTime): String {
    val tz = TimeZone.currentSystemDefault()
    val now = Clock.System.now()
    val target = LocalDateTime(
        sessionDate.year, sessionDate.monthNumber, sessionDate.dayOfMonth,
        alarmTime.hour, alarmTime.minute, alarmTime.second, alarmTime.nanosecond,
    ).toInstant(tz)
    val diff: Duration = target - now

    if (diff < 0.minutes) return "ringing now"
    if (diff < 60.minutes) return "in ${diff.inWholeMinutes} min"
    if (diff < 12.hours) {
        val hours = diff.inWholeHours
        val mins = (diff.inWholeMinutes % 60)
        return if (mins == 0L) "in ${hours}h" else "in ${hours}h ${mins}m"
    }
    val timeOfDay = when (alarmTime.hour) {
        in 0..4 -> "tonight"
        in 5..11 -> "tomorrow morning"
        in 12..16 -> "tomorrow afternoon"
        in 17..20 -> "tomorrow evening"
        else -> "tomorrow night"
    }
    return timeOfDay
}

/**
 * Narrative body paragraph with inline burnt-orange highlights for the
 * chosen alarm time, anchor event, snooze count, etc. Reads like a sentence
 * the way ref 1 and ref 2 do.
 */
@Composable
fun NarrativeSummary(
    state: SessionDisplayState?,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        shape = RoundedCornerShape(32.dp),
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = narrativeFor(state),
                style = MaterialTheme.typography.bodyLarge,
            )
            if (state != null && state.snoozeCount > 0) {
                PillBadge(
                    text = "Snoozed ×${state.snoozeCount}",
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}

@Composable
private fun narrativeFor(state: SessionDisplayState?) = when {
    state == null -> highlightedAnnotated(
        template = "Cron is idle. The next plan will run at your {{trigger}}.",
        highlights = mapOf("trigger" to "evening trigger"),
    )
    state.alarmTime == null -> highlightedAnnotated(
        template = "Cron is {{status}} tonight's alarm — hang tight while it reads your calendar and picks a wake time.",
        highlights = mapOf("status" to "still planning"),
    )
    state.action == ActionType.DoNothing -> highlightedAnnotated(
        template = "Sleep in tomorrow. No alarm scheduled — Cron will only ring you by {{floor}} as a safety floor.",
        highlights = mapOf("floor" to formatTime(state.alarmTime)),
    )
    state.action == ActionType.CancelAlarm -> highlightedAnnotated(
        template = "Alarm cancelled. The hard-latest at {{floor}} is still armed as a safety floor.",
        highlights = mapOf("floor" to formatTime(state.alarmTime)),
    )
    else -> highlightedAnnotated(
        template = "You're set to wake at {{time}}. Cron will ring through the lock screen and slide-to-dismiss.",
        highlights = mapOf("time" to formatTime(state.alarmTime)),
    )
}

private fun formatTime(time: LocalTime): String = "%02d:%02d".format(time.hour, time.minute)

/**
 * Collapsible disclosure for the AI's reasoning text. Replaces the
 * always-on AiPlanCard.
 */
@Composable
fun ReasoningDisclosure(
    state: SessionDisplayState?,
    modifier: Modifier = Modifier,
) {
    val reason = state?.reason?.takeIf { it.isNotBlank() } ?: return
    var expanded by rememberSaveable { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Cron's reasoning",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AnimatedVisibility(visible = expanded) {
            Text(
                text = reason,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            )
        }
    }
}

/**
 * Backwards-compat wrapper: the old `AiPlanCard` callsite now resolves
 * to the reasoning disclosure. Kept so HomeScreen doesn't need a
 * surgical import-list change in the same commit.
 */
@Composable
fun AiPlanCard(
    state: SessionDisplayState?,
    modifier: Modifier = Modifier,
) {
    ReasoningDisclosure(state = state, modifier = modifier)
}

/**
 * Backwards-compat wrapper for `SessionStatusCard`. Composes the new
 * header + narrative pair. New code should call them directly.
 */
@Composable
fun SessionStatusCard(
    state: SessionDisplayState?,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        HomeHeader(state = state)
        Spacer(modifier = Modifier.height(28.dp))
        NarrativeSummary(state = state)
    }
}
