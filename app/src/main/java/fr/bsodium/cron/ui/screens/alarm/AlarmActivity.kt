package fr.bsodium.cron.ui.screens.alarm

import android.app.KeyguardManager
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.bsodium.cron.receiver.AlarmReceiver
import fr.bsodium.cron.ui.theme.CronTheme
import fr.bsodium.cron.ui.theme.ExpressiveFontFamily
import fr.bsodium.cron.ui.theme.Radius
import fr.bsodium.cron.ui.theme.Spacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.util.Locale
import kotlin.math.roundToInt

private val THUMB_SIZE = 52.dp
private val THUMB_INSET = 6.dp
private val TRACK_HEIGHT = 64.dp

class AlarmActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Wake the screen and show above the keyguard
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            getSystemService(KeyguardManager::class.java)
                .requestDismissKeyguard(this, null)
        }
        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
        )

        val isLight = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) !=
            Configuration.UI_MODE_NIGHT_YES
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = isLight
            isAppearanceLightNavigationBars = isLight
        }

        val label = intent.getStringExtra(AlarmReceiver.EXTRA_LABEL) ?: "Cron Alarm"
        val requestCode = intent.getIntExtra(AlarmReceiver.EXTRA_REQUEST_CODE, 0)
        val snoozeCount = intent.getIntExtra(AlarmReceiver.EXTRA_SNOOZE_COUNT, 0)

        setContent {
            CronTheme {
                AlarmScreen(
                    label = label,
                    onDismiss = {
                        sendBroadcast(Intent(this@AlarmActivity, AlarmReceiver::class.java).apply {
                            action = AlarmReceiver.ACTION_DISMISS
                        })
                        finish()
                    },
                    onSnooze = {
                        sendBroadcast(Intent(this@AlarmActivity, AlarmReceiver::class.java).apply {
                            action = AlarmReceiver.ACTION_SNOOZE
                            putExtra(AlarmReceiver.EXTRA_REQUEST_CODE, requestCode)
                            putExtra(AlarmReceiver.EXTRA_LABEL, label)
                            putExtra(AlarmReceiver.EXTRA_SNOOZE_COUNT, snoozeCount)
                        })
                        finish()
                    },
                )
            }
        }
    }
}

@Composable
private fun AlarmScreen(
    label: String,
    onDismiss: () -> Unit,
    onSnooze: () -> Unit,
) {
    var timeText by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        while (true) {
            val t = LocalTime.now()
            timeText = String.format(Locale.US, "%02d:%02d", t.hour, t.minute)
            delay(1_000)
        }
    }
    AlarmScreenContent(timeText = timeText, label = label, onDismiss = onDismiss, onSnooze = onSnooze)
}

@Composable
private fun AlarmScreenContent(
    timeText: String,
    label: String,
    onDismiss: () -> Unit,
    onSnooze: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.primary),
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(
                text = timeText,
                fontFamily = ExpressiveFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 128.sp,
                color = MaterialTheme.colorScheme.onPrimary,
                style = androidx.compose.ui.text.TextStyle(
                    fontFeatureSettings = "tnum",
                ),
            )
            Spacer(modifier = Modifier.height(Spacing.xs))
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = Spacing.xxxl, vertical = Spacing.xxxl + Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            SlideToActTrack(
                label = "Slide to snooze",
                thumbColor = MaterialTheme.colorScheme.secondaryContainer,
                slideToEnd = false,
                onSlideComplete = onSnooze,
            )
            SlideToActTrack(
                label = "Slide to dismiss",
                thumbColor = MaterialTheme.colorScheme.onPrimary,
                slideToEnd = true,
                onSlideComplete = onDismiss,
            )
        }
    }
}

@Composable
private fun SlideToActTrack(
    label: String,
    thumbColor: Color,
    slideToEnd: Boolean,
    onSlideComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val thumbSizePx = with(density) { THUMB_SIZE.toPx() }
    val insetPx = with(density) { THUMB_INSET.toPx() }

    var trackWidthPx by remember { mutableStateOf(0f) }
    val maxOffset = (trackWidthPx - thumbSizePx - 2 * insetPx).coerceAtLeast(0f)
    val startOffset = if (slideToEnd) 0f else maxOffset

    val thumbAnim = remember { Animatable(0f) }
    LaunchedEffect(maxOffset) {
        if (maxOffset > 0f) thumbAnim.snapTo(startOffset)
    }

    val scope = rememberCoroutineScope()
    val onPrimary = MaterialTheme.colorScheme.onPrimary

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(TRACK_HEIGHT)
            .background(onPrimary.copy(alpha = 0.15f), Radius.full)
            .onSizeChanged { trackWidthPx = it.width.toFloat() },
    ) {
        Text(
            text = if (slideToEnd) "$label  →" else "←  $label",
            modifier = Modifier.align(Alignment.Center),
            style = MaterialTheme.typography.labelMedium,
            color = onPrimary.copy(alpha = 0.75f),
        )

        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset { IntOffset((insetPx + thumbAnim.value).roundToInt(), 0) }
                .size(THUMB_SIZE)
                .background(thumbColor, CircleShape)
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        scope.launch {
                            thumbAnim.snapTo((thumbAnim.value + delta).coerceIn(0f, maxOffset))
                        }
                    },
                    onDragStopped = {
                        scope.launch {
                            val completed = if (slideToEnd) {
                                thumbAnim.value >= maxOffset * 0.75f
                            } else {
                                thumbAnim.value <= maxOffset * 0.25f
                            }
                            if (completed) {
                                onSlideComplete()
                            } else {
                                thumbAnim.animateTo(
                                    startOffset,
                                    spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                                )
                            }
                        }
                    },
                ),
        )
    }
}

@Preview(showBackground = true, name = "Alarm — light")
@Preview(showBackground = true, name = "Alarm — dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AlarmScreenContentPreview() {
    CronTheme {
        AlarmScreenContent(
            timeText = "06:40",
            label = "Wake up",
            onDismiss = {},
            onSnooze = {},
        )
    }
}

@Preview(showBackground = true, name = "Slide to dismiss track")
@Composable
private fun SlideToActTrackPreview() {
    CronTheme {
        Box(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.primary)
                .padding(Spacing.xxxl),
        ) {
            SlideToActTrack(
                label = "Slide to dismiss",
                thumbColor = MaterialTheme.colorScheme.onPrimary,
                slideToEnd = true,
                onSlideComplete = {},
            )
        }
    }
}
