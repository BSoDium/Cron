package fr.bsodium.cron.ui.screens.home.components

import android.content.res.Configuration
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.bsodium.cron.session.model.SleepSegment
import fr.bsodium.cron.session.model.SleepStage
import fr.bsodium.cron.ui.components.PillBadge
import fr.bsodium.cron.ui.theme.CodeFontFamily
import fr.bsodium.cron.ui.theme.CronTheme
import fr.bsodium.cron.ui.theme.CronTypography
import fr.bsodium.cron.ui.theme.DisplayFontFamily
import fr.bsodium.cron.ui.theme.LcdFontFamily
import fr.bsodium.cron.ui.theme.Radius
import fr.bsodium.cron.ui.theme.Spacing
import fr.bsodium.cron.ui.theme.TightTextStyle
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun NextAlarmCard(
    dateLabel: String,
    alarmTime: LocalTime?,
    sleepDurationLabel: String?,
    sleepSegments: List<SleepSegment>,
    modifier: Modifier = Modifier,
) {
    AlarmShell(modifier) {
        AlarmCardContent(dateLabel, alarmTime, sleepDurationLabel, sleepSegments, alarmKind = alarmKindFor(alarmTime))
    }
}

/**
 * The bold-filled card shell — Material You primary fill, flat, rounded. [shape] is a parameter so
 * the collapsing variant can lerp its corner radius. Content renders in `onPrimary`.
 */
@Composable
internal fun AlarmShell(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(Radius.xl),
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primary,
        shape = shape,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        content = content,
    )
}

/** The expanded card body: bold date, hero LCD time, and the sleep block. Renders in `onPrimary`. */
@Composable
internal fun AlarmCardContent(
    dateLabel: String,
    alarmTime: LocalTime?,
    sleepDurationLabel: String?,
    sleepSegments: List<SleepSegment>,
    modifier: Modifier = Modifier,
    // The collapsing card hides this layer's time row (clock + countdown — both become moving copies
    // drawn on top) while still measuring it, so this stays the single source of expanded geometry.
    timeRowAlpha: Float = 1f,
    // The alarm-type badge sits in a row with the date (centred on it). In the collapsing card the
    // badge is hidden here (badgeAlpha = 0, still measured) and drawn as a moving copy on top.
    alarmKind: AlarmKind? = null,
    badgeAlpha: Float = 1f,
) {
    val onCard = MaterialTheme.colorScheme.onPrimary
    Column(
        modifier = modifier.padding(
            start = Spacing.xxl,
            top = Spacing.xl,
            end = Spacing.xxl,
            bottom = Spacing.xl,
        ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(BADGE_DATE_GAP),
        ) {
            if (alarmKind != null) {
                AlarmTypeBadge(kind = alarmKind, rotationDeg = 0f, modifier = Modifier.graphicsLayer { alpha = badgeAlpha })
            }
            AlignedFirstGlyph(
                text = dateLabel.ifBlank { "—" },
                color = onCard,
                style = CronTypography.dateLabel.copy(fontSize = 28.sp, lineHeight = 28.sp),
            )
        }
        LcdTimeDisplay(alarmTime = alarmTime, timeRowAlpha = timeRowAlpha)
        if (sleepSegments.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.xl))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Sleep",
                    fontFamily = CodeFontFamily,
                    fontSize = 18.sp,
                    color = onCard,
                    modifier = Modifier.weight(1f),
                )
                if (sleepDurationLabel != null) {
                    // Inverse pill on the bold card: on-color fill, primary text.
                    PillBadge(
                        text = sleepDurationLabel,
                        containerColor = onCard,
                        contentColor = MaterialTheme.colorScheme.primary,
                        textStyle = MaterialTheme.typography.labelMedium.copy(
                            fontFamily = CodeFontFamily,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                }
            }
            Spacer(Modifier.height(Spacing.md + Spacing.xs))
            SleepTimeline(segments = sleepSegments)
        }
    }
}

@Preview(showBackground = true, name = "Pending — no sleep data")
@Composable
private fun NextAlarmCardPendingPreview() {
    CronTheme {
        NextAlarmCard(
            dateLabel = "Monday 1",
            alarmTime = LocalTime(6, 40),
            sleepDurationLabel = null,
            sleepSegments = emptyList(),
            modifier = Modifier.padding(Spacing.xl),
        )
    }
}

/** A representative night ending at the 06:40 alarm, for previewing the sleep-duration state. */
internal val PREVIEW_SLEEP_SEGMENTS = listOf(
    SleepStage.Awake to ("2026-06-01T23:00:00Z" to "2026-06-01T23:12:00Z"),
    SleepStage.Light to ("2026-06-01T23:12:00Z" to "2026-06-02T00:30:00Z"),
    SleepStage.Deep to ("2026-06-02T00:30:00Z" to "2026-06-02T02:05:00Z"),
    SleepStage.Rem to ("2026-06-02T02:05:00Z" to "2026-06-02T02:50:00Z"),
    SleepStage.Light to ("2026-06-02T02:50:00Z" to "2026-06-02T04:10:00Z"),
    SleepStage.Deep to ("2026-06-02T04:10:00Z" to "2026-06-02T05:15:00Z"),
    SleepStage.Rem to ("2026-06-02T05:15:00Z" to "2026-06-02T06:05:00Z"),
    SleepStage.Light to ("2026-06-02T06:05:00Z" to "2026-06-02T06:40:00Z"),
).map { (stage, span) -> SleepSegment(stage, Instant.parse(span.first), Instant.parse(span.second)) }

@Preview(showBackground = true, name = "With sleep — light")
@Preview(showBackground = true, name = "With sleep — dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun NextAlarmCardWithSleepPreview() {
    CronTheme {
        NextAlarmCard(
            dateLabel = "Monday 1",
            alarmTime = LocalTime(6, 40),
            sleepDurationLabel = "7H 28M",
            sleepSegments = PREVIEW_SLEEP_SEGMENTS,
            modifier = Modifier.padding(Spacing.xl),
        )
    }
}

@Composable
private fun LcdTimeDisplay(alarmTime: LocalTime?, modifier: Modifier = Modifier, timeRowAlpha: Float = 1f) {
    // Tick once a minute so the countdown stays current without flickering.
    val countdown by produceState<HoursMinutes?>(initialValue = computeCountdown(alarmTime), alarmTime) {
        while (true) {
            value = computeCountdown(alarmTime)
            delay(60_000)
        }
    }
    val pending = alarmTime == null
    val progress = rememberLcdRevealProgress(alarmTime)
    val base = MaterialTheme.colorScheme.onPrimary
    val digitColor = if (pending) base.copy(alpha = 0.30f) else base
    // Pending: match the dimmed-digit alpha so "00H/00M" reads as a deliberate grayed twin; brighter once a real time shows.
    val countdownColor = if (pending) base.copy(alpha = 0.30f) else base.copy(alpha = 0.7f)

    Row(
        modifier = modifier.alpha(timeRowAlpha),
        verticalAlignment = Alignment.Top,
    ) {
        LcdClock(alarmTime = alarmTime, progress = progress, color = digitColor)
        CountdownStack(
            countdown = countdown,
            progress = progress,
            color = countdownColor,
            modifier = Modifier.padding(start = Spacing.xs + Spacing.xxs, top = Spacing.xs + Spacing.xxs),
        )
    }
}

/**
 * The "HH:MM" LCD clock (custom-colon, side-bearing-trimmed). Reused both inline in [LcdTimeDisplay]
 * and as the single moving element of the collapsing card, so [progress] (the reveal) is hoisted in.
 */
@Composable
internal fun LcdClock(
    alarmTime: LocalTime?,
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
    // The single-weight LCD face has no bold; [strokeWidthPx] (local px) overlays a stroke that
    // thickens the glyphs as the clock shrinks, so the small digits read with more weight.
    strokeWidthPx: Float = 0f,
) {
    val pending = alarmTime == null
    // Locale.US so the digits render as ASCII 0-9 on Arabic/Farsi/Bengali devices.
    val hh = if (pending) "00" else String.format(Locale.US, "%02d", (alarmTime.hour * progress).roundToInt())
    val mm = if (pending) "00" else String.format(Locale.US, "%02d", (alarmTime.minute * progress).roundToInt())
    val lcdStyle = TightTextStyle.copy(fontFamily = LcdFontFamily, fontSize = 76.sp, lineHeight = 76.sp)
    val ink = rememberLcdInkMetrics()
    // IntrinsicSize.Min so the colon's fillMaxHeight matches the digit line box, not the viewport.
    Row(modifier = modifier.height(IntrinsicSize.Min), verticalAlignment = Alignment.Top) {
        StrokeableLcdDigits(hh, color, lcdStyle, strokeWidthPx, alignFirstGlyph = true)
        ColonSeparator(
            color = color,
            dotBoostPx = strokeWidthPx,
            inkCenterFraction = ink.centerFraction,
            inkHeightFraction = ink.heightFraction,
            modifier = Modifier.padding(horizontal = Spacing.sm),
        )
        StrokeableLcdDigits(mm, color, lcdStyle, strokeWidthPx, alignFirstGlyph = false)
    }
}

/** Filled digits with an optional same-color stroke overlaid on top to fake extra weight. */
@Composable
private fun StrokeableLcdDigits(
    text: String,
    color: Color,
    style: TextStyle,
    strokeWidthPx: Float,
    alignFirstGlyph: Boolean,
) {
    Box {
        if (alignFirstGlyph) {
            AlignedFirstGlyph(text = text, color = color, style = style)
        } else {
            Text(text = text, color = color, style = style, maxLines = 1, softWrap = false)
        }
        if (strokeWidthPx > 0f) {
            val stroked = style.copy(
                drawStyle = Stroke(width = strokeWidthPx, join = StrokeJoin.Round, cap = StrokeCap.Round),
            )
            if (alignFirstGlyph) {
                AlignedFirstGlyph(text = text, color = color, style = stroked)
            } else {
                Text(text = text, color = color, style = stroked, maxLines = 1, softWrap = false)
            }
        }
    }
}

/**
 * The 700ms "roll up from 0" reveal progress, fired only when the alarm VALUE changes. The animated
 * key is [rememberSaveable], so reopening or switching tabs with an unchanged value shows it at rest.
 */
@Composable
internal fun rememberLcdRevealProgress(alarmTime: LocalTime?): Float {
    val valueKey = alarmTime?.let { it.hour * 60 + it.minute }
    var animatedKey by rememberSaveable { mutableStateOf<Int?>(null) }
    val progressAnim = remember { Animatable(if (valueKey == null || valueKey == animatedKey) 1f else 0f) }
    LaunchedEffect(valueKey) {
        if (valueKey == null) return@LaunchedEffect
        if (valueKey != animatedKey) {
            progressAnim.snapTo(0f)
            progressAnim.animateTo(1f, tween(durationMillis = LCD_REVEAL_MILLIS, easing = EaseOutCubic))
            animatedKey = valueKey
        } else {
            progressAnim.snapTo(1f)
        }
    }
    return progressAnim.value
}

internal data class HoursMinutes(val hours: Long, val minutes: Long)

/** "21H 41M" — time remaining until the next [alarmTime], or "--" when none is set. */
internal fun remainingLabel(alarmTime: LocalTime?): String {
    val c = computeCountdown(alarmTime) ?: return "--"
    return String.format(Locale.US, "%dH %02dM", c.hours, c.minutes)
}

private const val LCD_REVEAL_MILLIS = 700

/**
 * Two-line LCD stack ("8H" / "12M") showing time remaining until the alarm.
 * Renders dim placeholders when no alarm is set.
 */
@Composable
internal fun CountdownStack(
    countdown: HoursMinutes?,
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
) {
    // Space Grotesk (legible, unlike Major Mono's art-deco H/M); lineHeight < fontSize tightens the
    // H↔M leading so the stack is compact and gains breathing room when centred in the collapsed pill.
    val smallLcd = TightTextStyle.copy(
        fontFamily = DisplayFontFamily,
        fontSize = 24.sp,
        lineHeight = 21.sp,
    )
    // No alarm → a grayed "00H/00M" placeholder, mirroring the dimmed "00:00" digits.
    val (top, bottom) = if (countdown == null) "00H" to "00M"
    else String.format(Locale.US, "%dH", (countdown.hours * progress).roundToInt()) to
        String.format(Locale.US, "%dM", (countdown.minutes * progress).roundToInt())
    Text(
        text = "$top\n$bottom",
        color = color,
        style = smallLcd,
        maxLines = 2,
        softWrap = false,
        modifier = modifier,
    )
}

/**
 * Distance from `now` to the next occurrence of [alarmTime] (today if it's
 * still ahead, otherwise tomorrow). Returns null when no alarm is set.
 */
internal fun computeCountdown(alarmTime: LocalTime?): HoursMinutes? {
    if (alarmTime == null) return null
    val tz = TimeZone.currentSystemDefault()
    val nowLocal = Clock.System.now().toLocalDateTime(tz)
    val targetDate = if (alarmTime > nowLocal.time) nowLocal.date else nowLocal.date.plus(1, DateTimeUnit.DAY)
    val targetInstant = LocalDateTime(targetDate, alarmTime).toInstant(tz)
    val remaining = targetInstant - Clock.System.now()
    val totalMinutes = remaining.inWholeMinutes.coerceAtLeast(0)
    return HoursMinutes(hours = totalMinutes / 60, minutes = totalMinutes % 60)
}

private val COLON_WIDTH = 8.dp

/** Two dots centred on the digit ink (fills the row height) so the colon shares the digits' centre. */
@Composable
private fun ColonSeparator(
    color: Color,
    inkCenterFraction: Float,
    inkHeightFraction: Float,
    modifier: Modifier = Modifier,
    dotBoostPx: Float = 0f,
) {
    Canvas(modifier = modifier.width(COLON_WIDTH).fillMaxHeight()) {
        val cy = size.height * inkCenterFraction
        val gap = size.height * inkHeightFraction * 0.24f
        drawColonDot(color, centerY = cy - gap, boostPx = dotBoostPx)
        drawColonDot(color, centerY = cy + gap, boostPx = dotBoostPx)
    }
}

private fun DrawScope.drawColonDot(color: Color, centerY: Float, boostPx: Float = 0f) {
    val radius = size.width * 0.45f + boostPx * 0.5f
    drawCircle(
        color = color,
        radius = radius,
        center = Offset(x = size.width / 2f, y = centerY),
    )
}
