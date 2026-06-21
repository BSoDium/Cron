package fr.bsodium.cron.ui.screens.home.components

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.bsodium.cron.session.model.SessionStatus
import fr.bsodium.cron.ui.theme.CronTheme
import fr.bsodium.cron.ui.theme.Spacing

private val DOT_SIZE = 8.dp
private const val PULSE_DURATION_MS = 1500

@Composable
internal fun SessionStatusRow(
    status: SessionStatus?,
    isLast: Boolean,
    modifier: Modifier = Modifier,
) {
    val label = statusLabel(status)
    val baseColor = statusColor(status)
    val pulses = status == SessionStatus.Planning ||
        status == SessionStatus.Monitoring ||
        status == SessionStatus.ReMonitoring

    val dotColor = if (pulses) {
        val transition = rememberInfiniteTransition(label = "status-pulse")
        val animated by transition.animateColor(
            initialValue = baseColor,
            targetValue = baseColor.copy(alpha = 0.3f),
            animationSpec = infiniteRepeatable(
                animation = tween(PULSE_DURATION_MS),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "status-dot-alpha",
        )
        animated
    } else {
        baseColor
    }

    val firstLineHeight = with(LocalDensity.current) {
        MaterialTheme.typography.bodyMedium.lineHeight.toDp()
    }

    SessionTimelineRow(
        firstLineHeight = firstLineHeight,
        isFirst = true,
        isLast = isLast,
        icon = {
            Box(
                modifier = Modifier
                    .size(DOT_SIZE)
                    .clip(CircleShape)
                    .background(dotColor),
            )
        },
        discSize = DOT_SIZE,
        verticalPadding = Spacing.sm,
        modifier = modifier,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun statusColor(status: SessionStatus?): Color {
    val scheme = MaterialTheme.colorScheme
    return when (status) {
        SessionStatus.Planning -> scheme.tertiary
        SessionStatus.Monitoring, SessionStatus.ReMonitoring -> scheme.primary
        SessionStatus.Awake -> scheme.secondary
        SessionStatus.Complete, null -> scheme.onSurfaceVariant.copy(alpha = 0.5f)
    }
}

private fun statusLabel(status: SessionStatus?): String = when (status) {
    SessionStatus.Planning -> "Setting alarm…"
    SessionStatus.Monitoring -> "Monitoring sleep"
    SessionStatus.ReMonitoring -> "Monitoring sleep"
    SessionStatus.Awake -> "Good morning"
    SessionStatus.Complete -> "Session complete"
    null -> "Idle"
}

@Preview(showBackground = true, name = "StatusRow — monitoring")
@Composable
private fun StatusRowMonitoringPreview() {
    CronTheme {
        Column(modifier = Modifier.padding(horizontal = Spacing.xl)) {
            SessionStatusRow(status = SessionStatus.Monitoring, isLast = true)
        }
    }
}

@Preview(showBackground = true, name = "StatusRow — idle")
@Composable
private fun StatusRowIdlePreview() {
    CronTheme {
        Column(modifier = Modifier.padding(horizontal = Spacing.xl)) {
            SessionStatusRow(status = null, isLast = true)
        }
    }
}
