package fr.bsodium.cron.ui.screens.home

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import fr.bsodium.cron.CronApplication
import fr.bsodium.cron.ai.StreamingTurnStore
import fr.bsodium.cron.alarm.AlarmScheduler
import fr.bsodium.cron.alarm.EveningPlanScheduler
import fr.bsodium.cron.alarm.HardLatestScheduler
import fr.bsodium.cron.calendar.requestCalendarSync
import fr.bsodium.cron.location.LocationProvider
import fr.bsodium.cron.session.SessionFsm
import fr.bsodium.cron.session.SessionRepository
import fr.bsodium.cron.session.db.CronDatabase
import fr.bsodium.cron.session.db.toModel
import fr.bsodium.cron.service.SleepSessionService
import fr.bsodium.cron.session.model.ActionType
import fr.bsodium.cron.session.model.SessionStatus
import fr.bsodium.cron.session.model.EventData
import fr.bsodium.cron.session.model.Instruction
import fr.bsodium.cron.session.model.SessionEvent
import fr.bsodium.cron.session.model.TriggerType
import fr.bsodium.cron.settings.SettingsRepository
import fr.bsodium.cron.worker.AiTurnWorker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/** `timelineFlow`'s emitted value (Round 32) — the display-bound [capped] timeline plus which ids in
 *  it are genuinely new since the previous emission. A local pairing, not a field on [CappedTimeline]
 *  itself, which stays purely about truncation and is used standalone elsewhere. */
private data class TimelineFlowResult(val capped: CappedTimeline, val newlyArrivedIds: Set<String>)

/** Stateful wrapper around [diffNewlyArrivedIds] — the one piece of mutable bookkeeping the diff
 *  itself doesn't need to carry. Held as a [HomeViewModel] field specifically because it must survive
 *  Home's own composition being torn down and rebuilt (see the field's own KDoc at its declaration). */
private class NewlyArrivedIdTracker {
    private var previousIds: Set<String>? = null
    fun diff(currentIds: Set<String>): Set<String> =
        diffNewlyArrivedIds(currentIds, previousIds).also { previousIds = currentIds }
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val db = CronDatabase.get(application)
    private val repository = SessionRepository(application)
    private val settings = SettingsRepository(application)
    private val locationProvider = LocationProvider(application)
    private val eveningPlanScheduler = EveningPlanScheduler(application)
    private val alarmScheduler = AlarmScheduler(application)
    private val hardLatestScheduler = HardLatestScheduler(application)

    private val timelineRepo = TimelineRepository(db)

    private val _isRetrying = MutableStateFlow(false)

    private val _optimisticTurn = MutableStateFlow<Pair<String, Int>?>(null)
    private val _aiFailure = MutableStateFlow<AiTurnFailure?>(null)
    private val _lastFailedId = MutableStateFlow<String?>(null)
    private val _dismissedFailureId = MutableStateFlow<String?>(null)

    private val sessionFlow = db.sessionDao().observeLatest()

    private val aiPlanFlow = sessionFlow
        .map { it?.id }
        .distinctUntilChanged()
        .flatMapLatest { id ->
            if (id == null) {
                flowOf(null)
            } else {
                // One cache per session so settled (immutable) turns build once instead of being re-decoded on every DB/streaming emission; confined to this Default-dispatched flow.
                val threadCache = TurnThreadCache()
                combine(
                    db.aiMessageDao().observeBySession(id),
                    db.eventDao().observeBySession(id),
                    StreamingTurnStore.active,
                ) { rows, events, streaming ->
                    AiPlanMapper.buildPlan(
                        rows,
                        streaming?.takeIf { it.sessionId == id },
                        events.map { it.toModel() },
                        threadFor = threadCache::threadFor,
                    )
                }.conflate()
                    // Off the main thread: buildPlan decodes every turn's content JSON + every event's JSON.
                    .flowOn(Dispatchers.Default)
            }
        }

    private val currentEventsFlow = sessionFlow
        .map { it?.id }
        .distinctUntilChanged()
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList())
            else db.eventDao().observeBySession(id).map { rows -> rows.map { it.toModel() } }.flowOn(Dispatchers.Default)
        }

    private val _historicalSessions = MutableStateFlow<List<TimelineSession>>(emptyList())
    private val _moreHistoryAvailable = MutableStateFlow(false)
    private val _dismissedSettingsAt = MutableStateFlow(0L)

    private val settingsChangedFlow = combine(
        sessionFlow.map { it?.lastAiCallAt ?: 0L },
        settings.settingsUpdatedAt,
        _dismissedSettingsAt,
    ) { lastAiCallAt, settingsAt, dismissedAt ->
        settingsAt > lastAiCallAt && settingsAt > dismissedAt
    }

    private val streamingActiveFlow = combine(
        sessionFlow.map { it?.id }.distinctUntilChanged(),
        StreamingTurnStore.active,
    ) { id, streaming -> id != null && streaming?.sessionId == id }

    private val prefsFlow = combine(
        settings.hapticsEnabled,
        settings.autoAlarmsEnabled,
        settings.eveningTriggerLocalTime,
    ) { haptics, autoAlarms, eveningTrigger ->
        HomePrefs(
            hapticsEnabled = haptics,
            autoAlarmsEnabled = autoAlarms,
            eveningTriggerTime = eveningTrigger,
        )
    }

    /** Diffs each [timelineFlow] emission's ids against the previous one to find genuinely new
     *  arrivals. Held here, a plain ViewModel field, so it survives Home's own composition being torn
     *  down and rebuilt (e.g. a Home→Settings→back round trip disposes `HomePlanContent`'s
     *  remember-scoped state, but never this ViewModel — see `MainActivity.kt`'s
     *  `popUpTo(ROUTE_HOME) { inclusive = false }`). */
    private val newlyArrivedIdTracker = NewlyArrivedIdTracker()

    private val timelineFlow = combine(
        sessionFlow.map { it?.id }.distinctUntilChanged(),
        aiPlanFlow,
        currentEventsFlow,
        StreamingTurnStore.active,
        _historicalSessions,
    ) { sessionId, plan, events, streaming, history ->
        val dedupedHistory = if (sessionId != null && plan != null) {
            history.filter { it.sessionId != sessionId }
        } else {
            history
        }
        /** A fresh session's own turn 0 never has an intra-session previous time to compare
         *  against, so carry over the most recent older session's own last resolved alarm time —
         *  `dedupedHistory` is guaranteed most-recent-session-first (`SessionDao.findPaginated`
         *  orders by `createdAt DESC`), so the first match is the right one. */
        val carryOverPrev: LocalTime? = dedupedHistory.firstNotNullOfOrNull { session ->
            session.iterations.lastOrNull { it.thread.newAlarmTime != null }?.thread?.newAlarmTime
        }
        val currentSession = if (sessionId != null && plan != null) {
            val patchedIterations = if (carryOverPrev != null && plan.iterations.isNotEmpty()) {
                val first = plan.iterations.first()
                if (first.previousAlarmTime == null) {
                    listOf(first.copy(previousAlarmTime = carryOverPrev)) + plan.iterations.drop(1)
                } else {
                    plan.iterations
                }
            } else {
                plan.iterations
            }
            TimelineSession(
                sessionId = sessionId,
                iterations = patchedIterations,
                events = events,
                streamingTurnIndex = streaming?.takeIf { it.sessionId == sessionId }?.turnIndex,
            )
        } else {
            null
        }
        val allSessions = listOfNotNull(currentSession) + dedupedHistory
        val rawItems = buildTimeline(allSessions)
        /** Diffed on the uncapped id set, before `capTimeline` truncates: a genuinely new item is
         *  always sorted near the front and effectively never truncated away, and comparing against
         *  the previous *uncapped* set means an old item that scrolls back into the cap window
         *  purely because the cap boundary shifted is correctly not misreported as new. */
        val newlyArrived = newlyArrivedIdTracker.diff(rawItems.mapTo(mutableSetOf()) { it.id })
        TimelineFlowResult(capped = capTimeline(rawItems), newlyArrivedIds = newlyArrived)
    }.flowOn(Dispatchers.Default)

    private val statusFlow = combine(
        _isRetrying,
        settingsChangedFlow,
        _aiFailure,
        streamingActiveFlow,
        prefsFlow,
    ) { retrying, settingsChanged, failure, streaming, prefs ->
        HomeStatus(
            isRetrying = retrying || streaming,
            settingsChanged = settingsChanged,
            failure = failure,
            hapticsEnabled = prefs.hapticsEnabled,
            autoAlarmsEnabled = prefs.autoAlarmsEnabled,
            eveningTriggerTime = prefs.eveningTriggerTime,
        )
    }

    private val displayFlow = combine(
        sessionFlow.map { it?.toDisplayState() },
        settings.displayName,
    ) { session, displayName ->
        HomeDisplay(session = session, displayName = displayName)
    }

    val uiState: StateFlow<HomeUiState> = combine(
        displayFlow,
        aiPlanFlow,
        timelineFlow,
        statusFlow,
        _moreHistoryAvailable,
    ) { display, plan, timeline, status, moreHistoryAvailable ->
        HomeUiState(
            sessionDisplay = display.session,
            greetingPrefix = greetingPrefix(),
            greetingName = display.displayName,
            dateLabel = formatDateLabel(display.session, status.autoAlarmsEnabled),

            aiPlan = plan,
            timeline = timeline.capped.items,
            newlyArrivedIds = timeline.newlyArrivedIds,
            hasMoreHistory = timeline.capped.truncated || moreHistoryAvailable,
            isRetrying = status.isRetrying,
            initialized = true,
            settingsChangedSincePlan = status.settingsChanged && plan != null,
            aiFailure = status.failure,
            hapticsEnabled = status.hapticsEnabled,
            autoAlarmsEnabled = status.autoAlarmsEnabled,
            eveningTriggerTime = status.eveningTriggerTime,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    private fun loadRecentHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            val currentId = db.sessionDao().findCurrent()?.id
            val page = timelineRepo.loadHistory(
                excludeSessionId = currentId,
                limit = HISTORY_LOAD_SIZE,
                offset = 0,
            )
            _historicalSessions.value = page.sessions
            _moreHistoryAvailable.value = page.hasMore
        }
    }

    /** Hide the "settings changed" reminder until the next plan-affecting change. */
    fun dismissSettingsReminder() {
        _dismissedSettingsAt.value = Clock.System.now().toEpochMilliseconds()
    }

    /**
     * Toggle automatic alarm planning. ON re-arms the nightly trigger AND re-applies the current
     * session's already-decided alarm + safety floor to AlarmManager (so Android actually has them);
     * OFF cancels the trigger and tears down everything armed for the current session (the AI alarm,
     * the hard-latest safety alarm, and any in-flight AI turn) so nothing is left to ring.
     */
    fun setAutoAlarmsEnabled(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            settings.setAutoAlarmsEnabled(enabled)
            if (enabled) {
                eveningPlanScheduler.armNext()
                repository.findCurrent()?.let {
                    rearmSessionAlarms(it, alarmScheduler, hardLatestScheduler)
                }
            } else {
                eveningPlanScheduler.cancel()
                // Stop the monitor so sensors stop emitting — tearing down the FGS is the actual stand-down the user asked for (the FSM alone only drops automatic events).
                getApplication<Application>().startService(SleepSessionService.stopIntent(getApplication()))
                repository.findCurrent()?.let { session ->
                    alarmScheduler.cancel(session.date)
                    hardLatestScheduler.clear(session.date)
                    repository.cancelAiTurn(session.id)
                }
            }
        }
    }

    init {
        loadRecentHistory()

        viewModelScope.launch {
            sessionFlow
                .map { it?.status }
                .distinctUntilChanged()
                .collect { status ->
                    if (status == SessionStatus.Complete.name) loadRecentHistory()
                }
        }

        // Drive the "working" flag and failure banner off the real WorkManager turn, not a fixed delay: a tap sets isRetrying true optimistically, cleared on terminal state, surfacing a FAILED turn's output-data failure reason.
        viewModelScope.launch {
            sessionFlow.map { it?.id }.distinctUntilChanged()
                .flatMapLatest { id ->
                    if (id == null) flowOf(emptyList<WorkInfo>()) else repository.observeAiTurnWork(id)
                }
                .collect { infos ->
                    when {
                        infos.any { !it.state.isFinished } -> _isRetrying.value = true
                        infos.isNotEmpty() -> {
                            _isRetrying.value = false
                            if (infos.any { it.state == WorkInfo.State.FAILED }) {
                                _optimisticTurn.value?.let { (sid, turn) ->
                                    viewModelScope.launch(Dispatchers.IO) {
                                        db.aiMessageDao().deleteByTurn(sid, turn)
                                    }
                                }
                            }
                            clearOptimisticTurn() // safety net if the worker died before streaming
                        }
                    }

                    val failed = infos.firstOrNull { it.state == WorkInfo.State.FAILED }
                    _aiFailure.value = when {
                        // A turn is running/queued — hide any stale failure from the previous attempt.
                        infos.any { !it.state.isFinished } -> null
                        failed == null -> null
                        failed.id.toString() == _dismissedFailureId.value -> null
                        else -> {
                            _lastFailedId.value = failed.id.toString()
                            failed.outputData.toAiTurnFailure()
                        }
                    }
                }
        }
    }

    fun updateAlarmTime(newTime: LocalTime) {
        viewModelScope.launch(Dispatchers.IO) {
            val session = repository.findCurrent() ?: return@launch
            val tz = TimeZone.of(session.timezone)
            val requested = session.date.atTime(newTime).toInstant(tz)
            val plan = alarmScheduler.schedule(
                requested = requested,
                hardLatest = session.plan.hardLatest,
                sessionDate = session.date,
                timezone = tz,
                label = session.currentInstruction.reason.ifBlank { "Cron Alarm" },
                sessionId = session.id,
            )
            val actualTime = plan.actualInstant.toLocalDateTime(tz).time
            repository.updateInstruction(
                session.id,
                Instruction(
                    action = ActionType.SetAlarm,
                    alarmTime = actualTime,
                    reason = session.currentInstruction.reason,
                    issuedAt = Clock.System.now(),
                ),
            )
        }
    }

    /** Hide the AI failure banner until the next distinct turn fails (keyed on the failed work's id). */
    fun dismissAiFailure() {
        _dismissedFailureId.value = _lastFailedId.value
        _aiFailure.value = null
    }

    /**
     * Re-runs the AI alarm prediction for the active session, or bootstraps a
     * fresh session via the same evening-plan flow the nightly receiver uses
     * when no session exists. Spins the retry button while the worker runs.
     *
     * (We skip the alarm re-arm and foreground-service startup that
     * [fr.bsodium.cron.receiver.EveningPlanReceiver] also performs — those are
     * scheduling concerns owned by the receiver, not a manual user trigger.)
     */
    fun retryAiPlan() {
        _isRetrying.value = true
        // Application-scoped, NOT viewModelScope: a ViewModel clear could cancel this mid-flight (tens of seconds of location/calendar work sit between seed and enqueue) and orphan the process-global StreamingTurnStore seed as a permanent phantom tab; the finally below guarantees it's cleared whenever the worker never gets enqueued.
        getApplication<CronApplication>().appScope.launch {
            var seeded: Pair<String, Int>? = null
            var enqueued = false
            try {
                val tz = TimeZone.currentSystemDefault()
                val morning = SessionRepository.morningDate(Clock.System.now(), tz)
                // A same-morning session means this is a replan of an existing plan, not a fresh bootstrap.
                val replanSession = repository.findCurrent()?.takeIf { it.date == morning }
                // Seed the upcoming turn FIRST (fast DB read) so its tab shows the instant the user taps, before the slow location fetch; it IS the real turn since the worker uses maxTurn+1 too and appendEvent below doesn't touch AiMessage.
                if (replanSession != null) {
                    val nextTurn = (db.aiMessageDao().maxTurnIndex(replanSession.id) ?: -1) + 1
                    // Seed with the trigger we're about to fire, so the new tab reads "Re-planned" immediately instead of inheriting the prior event's label until ours is persisted.
                    StreamingTurnStore.seedPending(replanSession.id, nextTurn, Clock.System.now().toEpochMilliseconds(), TriggerType.EveningPlan)
                    seeded = replanSession.id to nextTurn
                    _optimisticTurn.value = seeded
                }
                // Nudge a calendar sync up front; the location fetch + AI round-trip below give it time to land.
                requestCalendarSync()
                // Manual replan captures a FRESH foreground fix — the user may have moved since the session began.
                val location = locationProvider.acquireForEveningPlan()
                val event = SessionEvent(
                    trigger = TriggerType.EveningPlan,
                    timestamp = Clock.System.now(),
                    // The FAB is always user-initiated — flags turn 0 as a manual plan in the UI.
                    data = EventData.EveningPlan(timezone = tz.id, location = location, isManual = true),
                )
                val fsm = SessionFsm(getApplication(), repository)
                if (replanSession != null) {
                    // Same-morning replan: append the fresh evening-plan event (latest wins in the worker), pull in any plan-affecting setting changed since bootstrap, then re-run the turn.
                    repository.appendEvent(replanSession.id, event)
                    fsm.refreshPlanFromSettings(replanSession.id)
                    repository.triggerAiTurn(replanSession.id)
                } else {
                    // No session, or a stale one targeting an earlier morning — let the FSM supersede it and bootstrap a fresh session for the correct upcoming morning.
                    fsm.onEvent(event)
                }
                enqueued = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "retryAiPlan failed before the worker was enqueued", e)
            } finally {
                // Once enqueued, the worker's own finally owns the streaming-store lifecycle; until then, every exit must roll the optimistic UI back or the seed wedges the home screen.
                if (!enqueued) {
                    seeded?.let { (sessionId, turn) -> StreamingTurnStore.clear(sessionId, turn) }
                    _optimisticTurn.value = null
                    _isRetrying.value = false
                }
            }
        }
    }

    /**
     * Interrupts the running AI turn. If no alarm was set, reverts the latest turn so the
     * thinking thread disappears and the home screen returns to its empty state.
     */
    fun cancelAiPlan() {
        _isRetrying.value = false
        clearOptimisticTurn()
        viewModelScope.launch(Dispatchers.IO) {
            val current = db.sessionDao().findCurrent() ?: return@launch
            repository.cancelAiTurn(current.id)
            if (current.toDisplayState()?.alarmTime == null) {
                db.aiMessageDao().maxTurnIndex(current.id)?.let { turn ->
                    db.aiMessageDao().deleteByTurn(current.id, turn)
                }
            }
        }
    }

    private fun clearOptimisticTurn() {
        _optimisticTurn.value?.let { (session, turn) -> StreamingTurnStore.clear(session, turn) }
        _optimisticTurn.value = null
    }

    private companion object {
        const val TAG = "HomeViewModel"
    }
}

private const val HISTORY_LOAD_SIZE = 10

