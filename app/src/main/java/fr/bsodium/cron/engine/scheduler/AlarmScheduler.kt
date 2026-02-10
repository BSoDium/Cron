package fr.bsodium.cron.engine.scheduler

import fr.bsodium.cron.engine.model.ScheduledAlarm

/**
 * Abstraction for scheduling and cancelling alarms.
 * Implementations use Android's [android.app.AlarmManager].
 */
interface AlarmScheduler {

    /** Schedule (or replace) an alarm. */
    fun schedule(alarm: ScheduledAlarm)

    /** Cancel a previously scheduled alarm by its request code. */
    fun cancel(requestCode: Int)

    /** Whether the app can schedule exact alarms on this device. */
    fun canScheduleExactAlarms(): Boolean
}
