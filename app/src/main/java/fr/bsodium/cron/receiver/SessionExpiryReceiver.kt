package fr.bsodium.cron.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import fr.bsodium.cron.alarm.AlarmConstants
import fr.bsodium.cron.session.SessionFsm
import fr.bsodium.cron.session.SessionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Fires once a session's active window closes with no further activity — a session that never got a
 * clean [fr.bsodium.cron.session.model.TriggerType.OutOfBedConfirmed] shouldn't stay open until
 * tomorrow's evening plan happens to supersede it. Purely janitorial: no ringing code anywhere near
 * this, unlike [AlarmReceiver] — it just asks the FSM whether the session is still within its window
 * and, if not, marks it complete.
 */
class SessionExpiryReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE) return
        val sessionId = intent.getStringExtra(AlarmConstants.EXTRA_SESSION_ID) ?: return

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val fsm = SessionFsm(context, SessionRepository(context))
                val completed = fsm.completeIfExpired(sessionId)
                Log.i(TAG, "Session expiry check for $sessionId — completed=$completed")
            } catch (t: Throwable) {
                Log.e(TAG, "Session expiry check failed for $sessionId", t)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_FIRE = "fr.bsodium.cron.SESSION_EXPIRY_FIRE"
        private const val TAG = "SessionExpiryReceiver"
    }
}
