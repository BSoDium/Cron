package fr.bsodium.cron.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import fr.bsodium.cron.alarm.EveningPlanScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Re-arms the evening plan trigger whenever the system timezone changes.
 *
 * Without this, a user who travels across timezones would have their
 * 22:00 trigger fire at the wrong local time. Re-arming with the new
 * system timezone ensures the next trigger resolves via the updated
 * ZoneRules and fires at the correct local time.
 */
class TimeZoneChangedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_TIMEZONE_CHANGED) return

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                EveningPlanScheduler(context).armNext()
                Log.i(TAG, "Re-armed evening plan after timezone change")
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to re-arm after timezone change", t)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val TAG = "TimeZoneChangedReceiver"
    }
}
