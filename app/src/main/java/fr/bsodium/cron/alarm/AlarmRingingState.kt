package fr.bsodium.cron.alarm

/**
 * Process-wide "is an alarm currently ringing" flag.
 *
 * Set when [fr.bsodium.cron.receiver.AlarmReceiver] fires the full-screen alarm UI, cleared on
 * dismiss or snooze. Lets [fr.bsodium.cron.sensors.ScreenStateMonitor] tell "the user is unlocking
 * specifically to silence the alarm that's currently ringing" (the keyguard-dismiss that precedes
 * every slide-to-dismiss gesture) apart from "the user genuinely got out of bed on their own" — only
 * the latter should synthesize an [fr.bsodium.cron.session.model.TriggerType.OutOfBedConfirmed] event.
 */
object AlarmRingingState {
    @Volatile
    var isRinging: Boolean = false
        private set

    fun markRinging() {
        isRinging = true
    }

    fun markNotRinging() {
        isRinging = false
    }
}
