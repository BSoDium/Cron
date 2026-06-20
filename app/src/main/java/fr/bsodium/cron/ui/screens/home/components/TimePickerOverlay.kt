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
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp as colorLerp
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.util.lerp
import fr.bsodium.cron.session.model.SleepSegment
import fr.bsodium.cron.ui.theme.Radius
import fr.bsodium.cron.ui.theme.Spacing
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import java.util.Locale
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
    sleepDurationLabel: String?,
    sleepSegments: List<SleepSegment>,
    hardLatest: LocalTime?,
    onDismiss: () -> Unit,
    onConfirm: (LocalTime) -> Unit,
) {
    val progress = remember { Animatable(0f) }
    val spatialSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()

    LaunchedEffect(visible) {
        if (visible) {
            progress.animateTo(1f, spatialSpec)
        } else if (progress.value > 0f) {
            progress.animateTo(0f, spatialSpec)
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
    val overLimit by remember(hardLatest) {
        derivedStateOf {
            hardLatest != null && (pickerState.hour > hardLatest.hour ||
                (pickerState.hour == hardLatest.hour && pickerState.minute > hardLatest.minute))
        }
    }

    val primary = MaterialTheme.colorScheme.primary
    val surfaceHigh = MaterialTheme.colorScheme.surfaceContainerHigh
    val timing = rememberAlarmTiming(alarmTime, sessionDate)
    val density = LocalDensity.current
    val radiusPx = with(density) { Radius.xl.toPx() }
    val paddingPx = with(density) { Spacing.xl.toPx() }

    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var pickerSize by remember { mutableStateOf(IntSize.Zero) }

    val pickerW = pickerSize.width + (paddingPx * 2).roundToInt()
    val pickerH = pickerSize.height + (paddingPx * 2).roundToInt()
    val origin = cardBounds ?: Rect(
        left = (containerSize.width - pickerW) / 2f,
        top = (containerSize.height - pickerH) / 2f,
        right = (containerSize.width + pickerW) / 2f,
        bottom = (containerSize.height + pickerH) / 2f,
    )
    val targetLeft = (containerSize.width - pickerW) / 2f
    val targetTop = (containerSize.height - pickerH) / 2f

    Box(Modifier.fillMaxSize().onSizeChanged { containerSize = it }) {
        // Scrim
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

        // Morphing surface + clipped content
        Box(
            Modifier
                .fillMaxSize()
                .drawWithMorphSurface(
                    progress = { progress.value },
                    origin = origin,
                    targetLeft = targetLeft,
                    targetTop = targetTop,
                    pickerW = pickerW.toFloat(),
                    pickerH = pickerH.toFloat(),
                    startColor = primary,
                    endColor = surfaceHigh,
                    radiusPx = radiusPx,
                ),
        ) {
            // Ghost — card content, fading out early
            Box(
                Modifier
                    .width(with(density) { origin.width.toDp() })
                    .graphicsLayer {
                        val p = progress.value.coerceIn(0f, 1f)
                        translationX = lerp(origin.left, targetLeft, p)
                        translationY = lerp(origin.top, targetTop, p)
                        alpha = (1f - p * 2.85f).coerceIn(0f, 1f)
                    },
            ) {
                AlarmCardContent(
                    dateLabel = dateLabel,
                    alarmTime = alarmTime,
                    timing = timing,
                    sleepDurationLabel = sleepDurationLabel,
                    sleepSegments = sleepSegments,
                )
            }

            // Picker — fading in late
            Box(
                Modifier
                    .onSizeChanged { pickerSize = it }
                    .graphicsLayer {
                        val p = progress.value.coerceIn(0f, 1f)
                        translationX = lerp(origin.left + paddingPx, targetLeft + paddingPx, p)
                        translationY = lerp(origin.top + paddingPx, targetTop + paddingPx, p)
                        alpha = ((p - 0.5f) * 2f).coerceIn(0f, 1f)
                    },
            ) {
                PickerDialogContent(
                    pickerState = pickerState,
                    hardLatest = hardLatest,
                    overLimit = overLimit,
                    onDismiss = { dismiss() },
                    onConfirm = { onConfirm(LocalTime(pickerState.hour, pickerState.minute)) },
                )
            }
        }
    }
}

private fun Modifier.drawWithMorphSurface(
    progress: () -> Float,
    origin: Rect,
    targetLeft: Float,
    targetTop: Float,
    pickerW: Float,
    pickerH: Float,
    startColor: Color,
    endColor: Color,
    radiusPx: Float,
) = this.drawWithContent {
    val p = progress().coerceIn(0f, 1f)
    val color = colorLerp(startColor, endColor, p)
    val left = lerp(origin.left, targetLeft, p)
    val top = lerp(origin.top, targetTop, p)
    val w = lerp(origin.width, pickerW, p)
    val h = lerp(origin.height, pickerH, p)
    val cr = CornerRadius(radiusPx)

    drawRoundRect(color, Offset(left, top), Size(w, h), cornerRadius = cr)

    clipPath(
        Path().apply { addRoundRect(RoundRect(left, top, left + w, top + h, cr)) },
    ) {
        this@drawWithContent.drawContent()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PickerDialogContent(
    pickerState: TimePickerState,
    hardLatest: LocalTime?,
    overLimit: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val lighterTypography = MaterialTheme.typography.copy(
        displayLarge = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Normal),
    )
    Column {
        Text(
            text = "Select time",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (hardLatest != null) {
            Text(
                text = String.format(Locale.US, "Latest: %02d:%02d", hardLatest.hour, hardLatest.minute),
                style = MaterialTheme.typography.labelMedium,
                color = if (overLimit) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(Spacing.lg))
        MaterialTheme(typography = lighterTypography) {
            TimePicker(state = pickerState)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onDismiss) { Text("Cancel") }
            TextButton(
                onClick = onConfirm,
                enabled = !overLimit,
            ) {
                Text("OK")
            }
        }
    }
}
