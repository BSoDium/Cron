package fr.bsodium.cron.debug.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import fr.bsodium.cron.CronApplication
import fr.bsodium.cron.debug.HistorySeeder
import kotlinx.coroutines.launch

/**
 * DEBUG-ONLY. Fires [HistorySeeder.seed] without navigating Settings first.
 *
 * Usage: `adb shell am broadcast -a fr.bsodium.cron.debug.TRIGGER_HISTORY_SEED` — works whether Home
 * is on screen or not, no tap coordinates. Registered dynamically by
 * [fr.bsodium.cron.debug.DebugReceivers], same rationale as [TimelineReproReceiver]: a manifest
 * `<receiver>` is blocked by Android's background execution limits unless the app happens to be
 * foregrounded at that exact moment.
 */
class HistorySeedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_TRIGGER_HISTORY_SEED) return

        val pending = goAsync()
        (context.applicationContext as CronApplication).appScope.launch {
            try {
                HistorySeeder.seed(context)
                Log.i(TAG, "History seed trigger fired")
            } catch (e: Exception) {
                Log.e(TAG, "History seed trigger failed", e)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_TRIGGER_HISTORY_SEED = "fr.bsodium.cron.debug.TRIGGER_HISTORY_SEED"
        private const val TAG = "HistorySeedReceiver"
    }
}
