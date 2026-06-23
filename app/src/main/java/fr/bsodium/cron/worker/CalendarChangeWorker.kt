package fr.bsodium.cron.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import fr.bsodium.cron.calendar.CalendarChangeAnalyzer
import fr.bsodium.cron.session.SessionRepository
import fr.bsodium.cron.settings.SettingsRepository
import kotlinx.coroutines.flow.first
import fr.bsodium.cron.session.model.EventData
import fr.bsodium.cron.session.model.SessionEvent
import fr.bsodium.cron.session.model.TriggerType
import kotlinx.datetime.Clock

/**
 * Runs after a calendar provider change event (debounced via WorkManager REPLACE).
 *
 * Computes the first-event signature for the current session's morning date
 * and compares it against the cached value. If the anchor event changed,
 * appends a [TriggerType.CalendarChange] event and triggers an AI replan.
 * If nothing changed, exits silently.
 */
class CalendarChangeWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (!SettingsRepository(applicationContext).autoAlarmsEnabledNow()) {
            Log.d(TAG, "Auto-plan disabled — skipping calendar change check")
            return Result.success()
        }
        val repository = SessionRepository(applicationContext)
        val session = repository.findCurrent() ?: run {
            Log.d(TAG, "No active session — skipping calendar change check")
            return Result.success()
        }

        val allowedRsvp = SettingsRepository(applicationContext).allowedRsvpStatuses.first()
        val analyzer = CalendarChangeAnalyzer(applicationContext.contentResolver, allowedRsvp)
        val diff = analyzer.analyze(session)

        if (!diff.firstEventChanged) {
            Log.d(TAG, "Calendar changed but first event unchanged (sig=${diff.newSig}) — no replan")
            return Result.success()
        }

        Log.i(TAG, "First event changed for session ${session.id}: ${session.cachedFirstEventSig} → ${diff.newSig}")

        val event = SessionEvent(
            trigger = TriggerType.CalendarChange,
            timestamp = Clock.System.now(),
            data = EventData.CalendarChange(
                changeType = "first_event_changed",
                eventId = diff.newSig?.substringBefore("|") ?: "",
                affectsFirstEvent = true,
            ),
        )
        repository.appendEventAndTriggerAi(session.id, event)
        repository.updateCachedFirstEventSig(session.id, diff.newSig)

        return Result.success()
    }

    companion object {
        const val NAME = "calendar_change_analyze"
        private const val TAG = "CalendarChangeWorker"
    }
}
