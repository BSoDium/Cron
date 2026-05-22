package fr.bsodium.cron.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import fr.bsodium.cron.alarm.EveningPlanScheduler
import fr.bsodium.cron.location.LocationProvider
import fr.bsodium.cron.session.SessionFsm
import fr.bsodium.cron.session.SessionRepository
import fr.bsodium.cron.session.model.EventData
import fr.bsodium.cron.session.model.SessionEvent
import fr.bsodium.cron.session.model.TriggerType
import fr.bsodium.cron.service.SleepSessionService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone

/**
 * Fires once per evening at the user's configured trigger time and
 * bootstraps a new sleep session.
 *
 * Steps:
 *  1. Re-arm tomorrow's trigger so the chain stays alive.
 *  2. Start [SleepSessionService] to host overnight sensor monitors.
 *  3. Acquire a one-shot location fix (non-blocking; fallback chain in [LocationProvider]).
 *  4. Emit an `evening_plan` event into [SessionFsm], which creates the session
 *     row and kicks the first AI turn.
 */
class EveningPlanReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE) return

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 1. Re-arm for tomorrow before doing anything else.
                EveningPlanScheduler(context).armNext()

                // 2. Start the foreground service.
                context.startForegroundService(SleepSessionService.startIntent(context))

                // 3. Acquire location — LocationProvider handles its own fallback chain.
                val location = LocationProvider(context).acquireForEveningPlan()

                // 4. Build and deliver the evening_plan event.
                val tz = TimeZone.currentSystemDefault()
                val now = Clock.System.now()
                val event = SessionEvent(
                    trigger = TriggerType.EveningPlan,
                    timestamp = now,
                    data = EventData.EveningPlan(
                        timezone = tz.id,
                        location = location,
                    ),
                )
                SessionFsm(context, SessionRepository(context)).onEvent(event)
                Log.i(TAG, "Evening plan session started (location_source=${location.source})")
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
