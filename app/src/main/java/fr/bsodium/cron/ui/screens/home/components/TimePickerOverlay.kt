package fr.bsodium.cron.ui.screens.home.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp as colorLerp
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import fr.bsodium.cron.ui.theme.CronTypography
import fr.bsodium.cron.ui.theme.Radius
import fr.bsodium.cron.ui.theme.Spacing
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.math.roundToInt

private const val SCRIM_ALPHA = 0.32f

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun TimePickerOverlay(
    visible: Boolean,
    cardBounds: Rect?,
    dateLabel: String,
    alarmTime: LocalTime?,
    sessionDate: LocalDate?,
    hardLatest: LocalTime?,
    onDismiss: () -> Unit,
    onConfirm: (LocalTime) -> Unit,
    onShowingChanged: (Boolean) -> Unit,
) {
    val progress = remember { Animatable(0f) }
    val spatialSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()

    LaunchedEffect(visible) {
        if (visible) {
            onShowingChanged(true)
            progress.animateTo(1f, spatialSpec)
        } else if (progress.value > 0f) {
            progress.animateTo(0f, spatialSpec)
            onShowingChanged(false)
        }
    }

    val showing = progress.value > 0f || visible
    if (!showing) return

    var dismissing by remember { mutableStateOf(false) }
    fun dismiss() {
        if (dismissing) return
        dismissing = true
        onDismiss()
    }
    LaunchedEffect(visible) { if (visible) dismissing = false }

    BackHandler(enabled = visible) { dismiss() }

    val pickerState = rememberTimePickerState(
        initialHour = alarmTime?.hour ?: 7,
        initialMinute = alarmTime?.minute ?: 0,
        is24Hour = true,
    )
    val pickerTime = LocalTime(pickerState.hour, pickerState.minute)
    val pickerTiming = rememberAlarmTiming(pickerTime, sessionDate)

    val overLimit by remember(hardLatest) {
        derivedStateOf {
            hardLatest != null && (pickerState.hour > hardLatest.hour ||
                (pickerState.hour == hardLatest.hour && pickerState.minute > hardLatest.minute))
        }
    }

    val primary = MaterialTheme.colorScheme.primary
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    val surfaceHigh = MaterialTheme.colorScheme.surfaceContainerHigh
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val density = LocalDensity.current
    val radiusPx = with(density) { Radius.xl.toPx() }
    val narrowingPx = with(density) { (Spacing.md * 2).toPx() }.roundToInt()

    val p = progress.value.coerceIn(0f, 1f)
    val surfaceColor = colorLerp(primary, surfaceHigh, p)
    val textColor = colorLerp(onPrimary, onSurface, p)
    val subTextColor = colorLerp(onPrimary.copy(alpha = 0.7f), onSurfaceVariant, p)

    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var contentSize by remember { mutableStateOf(IntSize.Zero) }

    val targetW = cardBounds?.width?.let { (it - narrowingPx).roundToInt() }
        ?: (containerSize.width - with(density) { (Spacing.xl * 2).toPx() }.roundToInt())
    val targetH = contentSize.height
    val origin = cardBounds ?: Rect(
        left = (containerSize.width - targetW) / 2f,
        top = (containerSize.height - targetH) / 2f,
        right = (containerSize.width + targetW) / 2f,
        bottom = (containerSize.height + targetH) / 2f,
    )
    val targetLeft = (containerSize.width - targetW) / 2f
    val targetTop = (containerSize.height - targetH) / 2f

    Box(Modifier.fillMaxSize().onSizeChanged { containerSize = it }) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = progress.value * SCRIM_ALPHA }
                .background(Color.Black)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ) { dismiss() },
        )

        Box(
            Modifier
                .fillMaxSize()
                .drawMorphSurface(
                    progress = { progress.value },
                    origin = origin,
                    targetLeft = targetLeft,
                    targetTop = targetTop,
                    targetW = targetW.toFloat(),
                    targetH = targetH.toFloat(),
                    color = surfaceColor,
                    radiusPx = radiusPx,
                ),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .onSizeChanged { contentSize = it }
                    .width(with(density) { targetW.toDp() })
                    .graphicsLayer {
                        translationX = lerp(origin.left, targetLeft, progress.value.coerceIn(0f, 1f))
                        translationY = lerp(origin.top, targetTop, progress.value.coerceIn(0f, 1f))
                    }
                    .padding(
                        start = Spacing.xxl,
                        top = Spacing.lg,
                        end = Spacing.xxl,
                        bottom = Spacing.lg,
                    ),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AlignedFirstGlyph(
                        text = dateLabel.ifBlank { "—" },
                        color = textColor,
                        style = CronTypography.dateLabel.copy(fontSize = 28.sp, lineHeight = 28.sp),
                    )
                    Button(
                        onClick = { onConfirm(pickerTime) },
                        enabled = !overLimit,
                        modifier = Modifier.graphicsLayer {
                            alpha = ((progress.value - 0.5f) * 2f).coerceIn(0f, 1f)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = primary,
                            contentColor = onPrimary,
                        ),
                    ) { Text("Save") }
                }

                val upcoming = pickerTiming is AlarmTiming.Upcoming
                val digitColor = if (upcoming) textColor else textColor.copy(alpha = 0.30f)
                val countdownColor = if (upcoming) subTextColor else subTextColor.copy(alpha = 0.30f)

                Row(verticalAlignment = Alignment.Top) {
                    LcdClock(
                        alarmTime = pickerTime,
                        reveal = LcdReveal(pickerState.hour, pickerState.minute, 1f),
                        color = digitColor,
                    )
                    RemainingOrStatus(
                        timing = pickerTiming,
                        progress = 1f,
                        color = countdownColor,
                        modifier = Modifier.padding(
                            start = Spacing.xs + Spacing.xxs,
                            top = Spacing.xs + Spacing.xxs,
                        ),
                    )
                }

                Spacer(
                    Modifier.height(Spacing.lg).graphicsLayer {
                        alpha = ((progress.value - 0.5f) * 2f).coerceIn(0f, 1f)
                    },
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clipToBounds()
                        .graphicsLayer {
                            alpha = ((progress.value - 0.5f) * 2f).coerceIn(0f, 1f)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    TimePicker(
                        state = pickerState,
                        modifier = Modifier.layout { measurable, constraints ->
                            val placeable = measurable.measure(constraints)
                            val dialH = placeable.width
                            val offset = (placeable.height - dialH).coerceAtLeast(0)
                            layout(placeable.width, dialH) {
                                placeable.place(0, -offset)
                            }
                        },
                    )
                }
            }
        }
    }
}

private fun Modifier.drawMorphSurface(
    progress: () -> Float,
    origin: Rect,
    targetLeft: Float,
    targetTop: Float,
    targetW: Float,
    targetH: Float,
    color: Color,
    radiusPx: Float,
) = this.drawWithContent {
    val p = progress().coerceIn(0f, 1f)
    val left = lerp(origin.left, targetLeft, p)
    val top = lerp(origin.top, targetTop, p)
    val w = lerp(origin.width, targetW, p)
    val h = lerp(origin.height, targetH, p)
    val cr = CornerRadius(radiusPx)

    drawRoundRect(color, Offset(left, top), Size(w, h), cornerRadius = cr)

    clipPath(Path().apply { addRoundRect(RoundRect(left, top, left + w, top + h, cr)) }) {
        this@drawWithContent.drawContent()
    }
}
