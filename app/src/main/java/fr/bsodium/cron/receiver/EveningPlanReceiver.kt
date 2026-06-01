package fr.bsodium.cron.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import fr.bsodium.cron.alarm.EveningPlanScheduler
import fr.bsodium.cron.service.SleepSessionService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone

/**
 * Fires once per evening at the user's configured trigger time and bootstraps a new sleep session.
 *
 * Steps:
 *  1. Re-arm tomorrow's trigger so the chain stays alive.
 *  2. Start [SleepSessionService] for the evening plan. The service hosts the overnight sensor
 *     monitors AND, as a location-typed foreground service, captures a FRESH location and emits the
 *     `evening_plan` event itself. Capturing inside the FGS (rather than here in the background
 *     receiver) is what lets `getCurrentLocation` get a fresh fix with only foreground permission.
 */
class EveningPlanReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE) return

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Re-arm for tomorrow before doing anything else.
                EveningPlanScheduler(context).armNext()
                // An exact-alarm broadcast may start a foreground service from the background.
                val tz = TimeZone.currentSystemDefault().id
                context.startForegroundService(SleepSessionService.eveningPlanIntent(context, tz))
                Log.i(TAG, "Evening plan service started")
            } catch (t: Throwable) {
                Log.e(TAG, "Evening plan setup failed", t)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_FIRE = "fr.bsodium.cron.EVENING_PLAN_FIRE"
        private const val TAG = "EveningPlanReceiver"
    }
}
