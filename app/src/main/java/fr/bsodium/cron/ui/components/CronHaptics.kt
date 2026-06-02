package fr.bsodium.cron.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * Thin central wrapper over [LocalHapticFeedback] so haptics route through one place (and the
 * system honours the user's device haptic setting automatically). [enabled] gates everything to a
 * no-op — the home streaming ticks pass the user's "Haptic feedback" preference, while nav/FAB
 * feedback stays always-on.
 */
class CronHaptics(private val haptic: HapticFeedback, private val enabled: Boolean) {

    /** A subtle progress tick — used per chunk while the assistant streams. */
    fun tick() = perform(HapticFeedbackType.SegmentFrequentTick)

    fun confirm() = perform(HapticFeedbackType.Confirm)

    fun reject() = perform(HapticFeedbackType.Reject)

    fun contextClick() = perform(HapticFeedbackType.ContextClick)

    private fun perform(type: HapticFeedbackType) {
        if (enabled) haptic.performHapticFeedback(type)
    }
}

@Composable
fun rememberCronHaptics(enabled: Boolean = true): CronHaptics {
    val haptic = LocalHapticFeedback.current
    return remember(haptic, enabled) { CronHaptics(haptic, enabled) }
}
