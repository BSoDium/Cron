package fr.bsodium.cron.debug.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import fr.bsodium.cron.CronApplication
import fr.bsodium.cron.ai.StreamingTurnStore
import fr.bsodium.cron.calendar.requestCalendarSync
import fr.bsodium.cron.session.SessionFsm
import fr.bsodium.cron.session.SessionRepository
import fr.bsodium.cron.session.db.CronDatabase
import fr.bsodium.cron.session.model.EventData
import fr.bsodium.cron.session.model.LocationPayload
import fr.bsodium.cron.session.model.LocationSource
import fr.bsodium.cron.session.model.SessionEvent
import fr.bsodium.cron.session.model.TriggerType
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone

/**
 * DEBUG-ONLY. Fires the same repository-layer replan path `HomeViewModel.retryAiPlan()` uses when
 * the Home FAB is tapped — including `StreamingTurnStore.seedPending`, which is what makes the
 * streaming placeholder row appear in the timeline immediately, before `AiTurnWorker` writes
 * anything to the DB. That placeholder's insertion is the exact event whose transition caused the
 * timeline-overflow bug (docs/color-roles.md Round 37); a trigger that skipped this call would
 * silently test a different mechanism than a real tap does.
 *
 * Deliberately skips `LocationProvider.acquireForEveningPlan()` (up to a 30s worst-case timeout,
 * unrelated to this bug) in favor of a synthetic [LocationPayload] — everything else mirrors
 * `retryAiPlan()` exactly, including `requestCalendarSync()` and (for a replan)
 * `SessionFsm.refreshPlanFromSettings`.
 *
 * Usage: `adb shell am broadcast -a fr.bsodium.cron.debug.TRIGGER_AI_TURN` — fires while Home is
 * already composed and on-screen, no navigation, no tap coordinates. Uses whichever "Mock API
 * responses" setting is currently active, same as a real tap would.
 */
class TimelineReproReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_TRIGGER_AI_TURN) return

        val pending = goAsync()
        (context.applicationContext as CronApplication).appScope.launch {
            try {
                requestCalendarSync()

                val repository = SessionRepository(context)
                val tz = TimeZone.currentSystemDefault()
                val morning = SessionRepository.morningDate(Clock.System.now(), tz)
                val replanSession = repository.findCurrent()?.takeIf { it.date == morning }

                val location = LocationPayload(
                    lat = 0.0,
                    lng = 0.0,
                    accuracyMeters = null,
                    source = LocationSource.Unavailable,
                    capturedAt = Clock.System.now(),
                    address = null,
                )
                val event = SessionEvent(
                    trigger = TriggerType.EveningPlan,
                    timestamp = Clock.System.now(),
                    data = EventData.EveningPlan(timezone = tz.id, location = location, isManual = true),
                )

                val fsm = SessionFsm(context, repository)
                if (replanSession != null) {
                    val db = CronDatabase.get(context)
                    val nextTurn = (db.aiMessageDao().maxTurnIndex(replanSession.id) ?: -1) + 1
                    StreamingTurnStore.seedPending(
                        replanSession.id,
                        nextTurn,
                        Clock.System.now().toEpochMilliseconds(),
                        TriggerType.EveningPlan,
                    )
                    repository.appendEvent(replanSession.id, event)
                    fsm.refreshPlanFromSettings(replanSession.id)
                    repository.triggerAiTurn(replanSession.id)
                } else {
                    fsm.onEvent(event)
                }
                Log.i(TAG, "Repro trigger fired (replan=${replanSession != null})")
            } catch (e: Exception) {
                Log.e(TAG, "Repro trigger failed", e)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_TRIGGER_AI_TURN = "fr.bsodium.cron.debug.TRIGGER_AI_TURN"
        private const val TAG = "TimelineReproReceiver"
    }
}
