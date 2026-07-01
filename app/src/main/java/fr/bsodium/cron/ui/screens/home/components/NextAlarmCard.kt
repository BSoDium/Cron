package fr.bsodium.cron.ui.screens.home.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.bsodium.cron.ui.theme.CronTheme
import fr.bsodium.cron.ui.theme.CronTypography
import fr.bsodium.cron.ui.theme.Radius
import fr.bsodium.cron.ui.theme.Spacing
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import java.util.Locale
import kotlin.math.roundToInt
import androidx.compose.ui.util.lerp

@Composable
fun NextAlarmCard(
    dateLabel: String,
    alarmTime: LocalTime?,
    sessionDate: LocalDate?,
    modifier: Modifier = Modifier,
) {
    val timing = rememberAlarmTiming(alarmTime, sessionDate)
    AlarmShell(modifier) {
        AlarmCardContent(dateLabel, alarmTime, timing)
    }
}

/**
 * The bold-filled card shell — Material You primary fill, flat, rounded. [shape] is a parameter so the
 * collapsing variant can lerp its corner radius. Content renders in `onPrimary`.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun AlarmShell(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(Radius.xl),
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val commonModifier = modifier.fillMaxWidth()
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = commonModifier,
            color = MaterialTheme.colorScheme.primary,
            shape = shape,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            content = content,
        )
    } else {
        Surface(
            modifier = commonModifier,
            color = MaterialTheme.colorScheme.primary,
            shape = shape,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            content = content,
        )
    }
}

/** The expanded card body: bold date, hero LCD time, and the sleep block. Renders in `onPrimary`. */
@Composable
internal fun AlarmCardContent(
    dateLabel: String,
    alarmTime: LocalTime?,
    timing: AlarmTiming,
    modifier: Modifier = Modifier,
    // The collapsing card hides this layer's time row (clock + countdown — both become moving copies
    // drawn on top) while still measuring it, so this stays the single source of expanded geometry.
    timeRowAlpha: Float = 1f,
    // The date is hidden here (dateAlpha = 0) in the collapsing card and drawn as a moving copy that
    // slides up out the top, so it leaves/enters opposite the time (which moves from the bottom).
    dateAlpha: Float = 1f,
) {
    val onCard = MaterialTheme.colorScheme.onPrimary
    Column(
        modifier = modifier.padding(
            start = Spacing.xxl,
            top = Spacing.lg,
            end = Spacing.xxl,
            bottom = Spacing.lg,
        ),
    ) {
        var initialRender by remember { mutableStateOf(true) }
        LaunchedEffect(Unit) { initialRender = false }
        val displayLabel = alarmSentenceForTiming(dateLabel, timing).ifBlank { "—" }
        if (initialRender) {
            DateSentenceLabel(
                text = displayLabel,
                color = onCard.copy(alpha = 0.9f),
                style = CronTypography.dateSentence,
                modifier = Modifier
                    .padding(bottom = Spacing.xs)
                    .graphicsLayer { alpha = dateAlpha },
            )
        } else {
            Crossfade(
                targetState = displayLabel,
                animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
                label = "date-label-crossfade",
                modifier = Modifier
                    .padding(bottom = Spacing.xs)
                    .graphicsLayer { alpha = dateAlpha },
            ) { label ->
                DateSentenceLabel(
                    text = label,
                    color = onCard.copy(alpha = 0.9f),
                    style = CronTypography.dateSentence,
                )
            }
        }
        LcdTimeDisplay(alarmTime = alarmTime, timing = timing, base = onCard, timeRowAlpha = timeRowAlpha)
    }
}

@Preview(showBackground = true, name = "Alarm set")
@Composable
private fun NextAlarmCardPreview() {
    CronTheme {
        NextAlarmCard(
            dateLabel = "Monday 1",
            alarmTime = LocalTime(6, 40),
            sessionDate = null,
            modifier = Modifier.padding(Spacing.xl),
        )
    }
}

@Preview(showBackground = true, name = "Spent — woke up")
@Composable
private fun NextAlarmCardSpentPreview() {
    CronTheme {
        NextAlarmCard(
            dateLabel = "Today, you'll wake up at",
            alarmTime = LocalTime(6, 40),
            sessionDate = LocalDate(2020, 1, 1),
            modifier = Modifier.padding(Spacing.xl),
        )
    }
}

@Composable
private fun LcdTimeDisplay(
    alarmTime: LocalTime?,
    timing: AlarmTiming,
    base: Color,
    modifier: Modifier = Modifier,
    timeRowAlpha: Float = 1f,
) {
    val reveal = rememberLcdReveal(alarmTime)
    // Only an upcoming alarm reads in full; no alarm and a passed alarm both render the grayed onset look.
    val upcoming = timing is AlarmTiming.Upcoming
    val digitColor = if (upcoming) base else base.copy(alpha = 0.30f)
    val statusColor = if (upcoming) base.copy(alpha = 0.7f) else base.copy(alpha = 0.30f)

    Row(
        modifier = modifier.alpha(timeRowAlpha),
        verticalAlignment = Alignment.Top,
    ) {
        LcdClock(alarmTime = alarmTime, reveal = reveal, color = digitColor)
        RemainingOrStatus(
            timing = timing,
            progress = reveal.progress,
            color = statusColor,
            modifier = Modifier.padding(start = Spacing.xs + Spacing.xxs, top = Spacing.xs + Spacing.xxs),
        )
    }
}

/**
 * The "HH:MM" LCD clock (custom-colon, side-bearing-trimmed). Reused both inline in [LcdTimeDisplay]
 * and as one of the moving copies of the collapsing card, so [progress] (the reveal) is hoisted in.
 */
@Composable
internal fun LcdClock(
    alarmTime: LocalTime?,
    reveal: LcdReveal,
    color: Color,
    modifier: Modifier = Modifier,
    // The single-weight LCD face has no bold; [strokeWidthPx] (local px) overlays a stroke that
    // thickens the glyphs as the clock shrinks, so the small digits read with more weight.
    strokeWidthPx: Float = 0f,
) {
    val pending = alarmTime == null
    // Locale.US so the digits render as ASCII 0-9 on Arabic/Farsi/Bengali devices.
    val hh = if (pending) "00" else String.format(Locale.US, "%02d", lerp(reveal.fromHour.toFloat(), alarmTime.hour.toFloat(), reveal.progress).roundToInt())
    val mm = if (pending) "00" else String.format(Locale.US, "%02d", lerp(reveal.fromMinute.toFloat(), alarmTime.minute.toFloat(), reveal.progress).roundToInt())
    val lcdStyle = CronTypography.lcdHero
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

/** Filled digits with an optional same-color stroke overlaid on top to fake extra weight.
 *  Offscreen compositing prevents double-alpha at fill/stroke overlap when [color] is translucent. */
@Composable
private fun StrokeableLcdDigits(
    text: String,
    color: Color,
    style: TextStyle,
    strokeWidthPx: Float,
    alignFirstGlyph: Boolean,
) {
    val needsOffscreen = strokeWidthPx > 0f && color.alpha < 1f
    val drawColor = if (needsOffscreen) color.copy(alpha = 1f) else color
    Box(
        modifier = if (needsOffscreen) {
            Modifier.graphicsLayer {
                alpha = color.alpha
                compositingStrategy = CompositingStrategy.Offscreen
            }
        } else Modifier,
    ) {
        if (alignFirstGlyph) {
            AlignedFirstGlyph(text = text, color = drawColor, style = style)
        } else {
            Text(text = text, color = drawColor, style = style, maxLines = 1, softWrap = false)
        }
        if (strokeWidthPx > 0f) {
            val stroked = style.copy(
                drawStyle = Stroke(width = strokeWidthPx, join = StrokeJoin.Round, cap = StrokeCap.Round),
            )
            if (alignFirstGlyph) {
                AlignedFirstGlyph(text = text, color = drawColor, style = stroked)
            } else {
                Text(text = text, color = drawColor, style = stroked, maxLines = 1, softWrap = false)
            }
        }
    }
}

internal data class LcdReveal(
    val fromHour: Int,
    val fromMinute: Int,
    val progress: Float,
)

@Composable
internal fun rememberLcdReveal(alarmTime: LocalTime?): LcdReveal {
    val valueKey = alarmTime?.let { it.hour * 60 + it.minute }
    var animatedKey by rememberSaveable { mutableStateOf<Int?>(null) }
    var fromKey by rememberSaveable { mutableStateOf(valueKey ?: 0) }
    val progressAnim = remember { Animatable(if (valueKey == null || valueKey == animatedKey) 1f else 0f) }
    LaunchedEffect(valueKey) {
        if (valueKey == null) return@LaunchedEffect
        if (animatedKey == null) {
            fromKey = valueKey
            progressAnim.snapTo(1f)
            animatedKey = valueKey
        } else if (valueKey != animatedKey) {
            fromKey = requireNotNull(animatedKey) { "checked non-null by the enclosing else-if" }
            progressAnim.snapTo(0f)
            // Sanctioned motionScheme exception (docs/expressive.md § Sanctioned exceptions): the reveal
            // gates digit rolling on a 0→1 progress; a spring's overshoot past 1 would re-roll the digits.
            progressAnim.animateTo(1f, tween(durationMillis = LCD_REVEAL_MILLIS, easing = EaseOutCubic))
            fromKey = valueKey
            animatedKey = valueKey
        } else {
            progressAnim.snapTo(1f)
        }
    }
    return LcdReveal(
        fromHour = fromKey / 60,
        fromMinute = fromKey % 60,
        progress = progressAnim.value,
    )
}

private const val LCD_REVEAL_MILLIS = 700

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

/** Renders the date sentence with the day portion (before the comma) bold and bright,
 *  the suffix thinner and dimmer. */
@Composable
internal fun DateSentenceLabel(
    text: String,
    color: Color,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    val commaIdx = text.indexOf(',')
    if (commaIdx > 0) {
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(text.substring(0, commaIdx)) }
                withStyle(SpanStyle(fontWeight = FontWeight.Light, color = color.copy(alpha = color.alpha * 0.7f))) {
                    append(text.substring(commaIdx))
                }
            },
            color = color,
            style = style,
            maxLines = 1,
            modifier = modifier,
        )
    } else {
        Text(
            text = text,
            color = color.copy(alpha = color.alpha * 0.7f),
            style = style.copy(fontWeight = FontWeight.Light),
            maxLines = 1,
            modifier = modifier,
        )
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
