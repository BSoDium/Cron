package fr.bsodium.cron.debug

import android.content.Context
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import fr.bsodium.cron.debug.receiver.TimelineReproReceiver

/**
 * DEBUG variant — registers [TimelineReproReceiver] dynamically (`Context.registerReceiver`, not a
 * manifest `<receiver>`) so `adb shell am broadcast` can reach it while the app is backgrounded.
 * Android's background execution limits (8.0+) block most implicit broadcasts to manifest-declared
 * receivers unless the app is in the foreground at that exact moment — a dynamically-registered
 * receiver is tied to this already-running process instead, so it's exempt. `RECEIVER_EXPORTED` is
 * required for an external `adb shell` caller (a different UID) to reach it at all.
 */
object DebugReceivers {
    fun register(context: Context) {
        ContextCompat.registerReceiver(
            context,
            TimelineReproReceiver(),
            IntentFilter(TimelineReproReceiver.ACTION_TRIGGER_AI_TURN),
            ContextCompat.RECEIVER_EXPORTED,
        )
    }
}
