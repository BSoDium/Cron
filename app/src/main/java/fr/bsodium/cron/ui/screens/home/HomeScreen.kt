package fr.bsodium.cron.ui.screens.home

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import fr.bsodium.cron.FabRegistry
import fr.bsodium.cron.R
import fr.bsodium.cron.ui.components.FabAction
import fr.bsodium.cron.ui.screens.home.components.AiThinkingThread
import fr.bsodium.cron.ui.screens.home.components.GreetingHeader
import fr.bsodium.cron.ui.screens.home.components.NextAlarmCard
import fr.bsodium.cron.ui.theme.CronTypography
import fr.bsodium.cron.ui.theme.Radius
import fr.bsodium.cron.ui.theme.Spacing

@Composable
fun HomeScreen(viewModel: HomeViewModel, fabRegistry: FabRegistry) {
    val uiState by viewModel.uiState.collectAsState()
    DisposableEffect(viewModel, fabRegistry) {
        fabRegistry.set(FabAction(onClick = viewModel::retryAiPlan, spinning = false))
        onDispose { fabRegistry.clear() }
    }
    LaunchedEffect(uiState.isRetrying, fabRegistry) {
        fabRegistry.set(FabAction(onClick = viewModel::retryAiPlan, spinning = uiState.isRetrying))
    }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasNotificationPermission by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasNotificationPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val navInsetBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val statusInsetTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    // The alarm card behaves like CSS `position: sticky; top: statusBar + gap`: the list scrolls
    // edge-to-edge under the status bar while the card flows then sticks just below it. It's an
    // overlay, not a stickyHeader (which pins at y=0 and can't take a top offset without a rest
    // gap or pin jump); an invisible "alarm-spacer" holds the card's place in the list flow.
    var cardHeightPx by remember { mutableStateOf(0) }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Spacing.xl,
                end = Spacing.xl,
                top = statusInsetTop + Spacing.xxl,
                bottom = navInsetBottom + Spacing.navBarClearance,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.xl),
        ) {
            item(key = "greeting") {
                GreetingHeader(
                    prefix = uiState.greetingPrefix,
                    name = uiState.greetingName,
                    photoUrl = uiState.greetingPhotoUrl,
                )
            }
            // Reserves the alarm card's flow space; the card itself is the StickyAlarm overlay.
            item(key = "alarm-spacer") {
                Spacer(Modifier.height(with(density) { cardHeightPx.toDp() }))
            }
            item(key = "thread") {
                val thread = uiState.aiThread
                if (thread != null) {
                    AiThinkingThread(thread)
                } else {
                    EmptyPlanState(
                        onRun = viewModel::retryAiPlan,
                        isRunning = uiState.isRetrying,
                    )
                }
            }
            if (!hasNotificationPermission) {
                item(key = "notif-permission") {
                    NotificationPermissionRow(
                        onEnable = {
                            context.startActivity(
                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                }
                            )
                        },
                    )
                }
            }
        }

        StickyAlarm(
            listState = listState,
            safeTop = statusInsetTop + Spacing.sm,
            cardHeightPx = cardHeightPx,
            onHeightChanged = { cardHeightPx = it },
        ) {
            NextAlarmCard(
                dateLabel = uiState.dateLabel,
                alarmTime = uiState.sessionDisplay?.alarmTime,
                sleepDurationLabel = uiState.sleepStats?.durationLabel,
                sleepSegments = uiState.sleepStats?.segments.orEmpty(),
            )
        }
    }
}

private data class StickyAlarmState(val top: Int, val gradientAlpha: Float)

/**
 * CSS-`sticky`-with-top-offset alarm card, rendered as an overlay over the list. It follows the
 * flow position of the "alarm-spacer" item, then holds at [safeTop] (just below the status bar).
 * When stuck, a full-width background→transparent gradient fades in behind it, dissolving content
 * that slides under the card (and the full-bleed pill's edges) into the page background.
 */
@Composable
private fun BoxScope.StickyAlarm(
    listState: LazyListState,
    safeTop: Dp,
    cardHeightPx: Int,
    onHeightChanged: (Int) -> Unit,
    card: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val safeTopPx = with(density) { safeTop.roundToPx() }
    val fadePx = with(density) { Spacing.xxl.toPx() }
    val state by remember(safeTopPx, fadePx) {
        derivedStateOf {
            val info = listState.layoutInfo
            // Item offsets are content-relative; the on-screen top = offset - viewportStartOffset
            // (viewportStartOffset is -beforeContentPadding). Null when the spacer has scrolled
            // off the top → fully stuck (handled explicitly to avoid sentinel-int overflow).
            val screenTop = info.visibleItemsInfo.firstOrNull { it.key == "alarm-spacer" }
                ?.let { it.offset - info.viewportStartOffset }
            if (screenTop == null) {
                StickyAlarmState(top = safeTopPx, gradientAlpha = 1f)
            } else {
                StickyAlarmState(
                    top = maxOf(safeTopPx, screenTop),
                    gradientAlpha = ((safeTopPx - screenTop) / fadePx).coerceIn(0f, 1f),
                )
            }
        }
    }
    val background = MaterialTheme.colorScheme.background
    // Solid background-colour from the top all the way down to the card's bottom, then a soft
    // fade to transparent just below it — so nothing is visible above/behind the pinned card and
    // content dissolves as it slides out the bottom.
    val cardBottomPx = safeTopPx + cardHeightPx
    val belowFadePx = with(density) { Spacing.xxxl.toPx() }
    val totalPx = cardBottomPx + belowFadePx
    val solidStop = if (totalPx > 0f) (cardBottomPx / totalPx).coerceIn(0f, 1f) else 1f
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(with(density) { totalPx.toDp() })
            .graphicsLayer { alpha = state.gradientAlpha }
            .background(
                Brush.verticalGradient(
                    0f to background,
                    solidStop to background,
                    1f to Color.Transparent,
                ),
            ),
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { translationY = state.top.toFloat() }
            .padding(horizontal = Spacing.xl)
            .onSizeChanged { if (it.height != cardHeightPx) onHeightChanged(it.height) },
    ) { card() }
}

/**
 * First-run state: Cron hasn't planned a wake-up yet. Explains what a plan needs
 * and offers a CTA that kicks off the same AI run as the play FAB.
 */
@Composable
private fun EmptyPlanState(
    onRun: () -> Unit,
    isRunning: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_thinking),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(Spacing.lg))
        Text(
            text = "No plan yet",
            style = CronTypography.pageTitle,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Spacing.sm))
        Text(
            text = "Cron reads your calendar and last night's sleep to pick the " +
                "smartest wake-up time. Run it to plan your morning.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Spacing.xl))
        Button(onClick = onRun, enabled = !isRunning) {
            if (isRunning) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(Modifier.width(Spacing.sm))
            }
            Text(if (isRunning) "Planning…" else "Plan my morning")
        }
    }
}

@Composable
private fun NotificationPermissionRow(onEnable: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(Radius.lg),
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Notifications are off — Cron can't ring your alarm",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onEnable) { Text("Enable") }
        }
    }
}
