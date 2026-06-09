package fr.bsodium.cron.ui.screens.home

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Data
import androidx.work.WorkInfo
import fr.bsodium.cron.ai.StreamingTurnStore
import fr.bsodium.cron.alarm.AlarmScheduler
import fr.bsodium.cron.alarm.EveningPlanScheduler
import fr.bsodium.cron.alarm.HardLatestScheduler
import fr.bsodium.cron.calendar.requestCalendarSync
import fr.bsodium.cron.location.LocationProvider
import fr.bsodium.cron.session.SessionFsm
import fr.bsodium.cron.session.SessionRepository
import fr.bsodium.cron.session.db.CronDatabase
import fr.bsodium.cron.session.db.SessionEntity
import fr.bsodium.cron.session.db.SessionEventEntity
import fr.bsodium.cron.session.db.SessionJson
import fr.bsodium.cron.session.db.toModel
import fr.bsodium.cron.session.model.ActionType
import fr.bsodium.cron.session.model.EventData
import fr.bsodium.cron.session.model.Instruction
import fr.bsodium.cron.session.model.SessionEvent
import fr.bsodium.cron.session.model.SessionStatus
import fr.bsodium.cron.session.model.SleepSegment
import fr.bsodium.cron.session.model.TriggerType
import fr.bsodium.cron.settings.SettingsRepository
import fr.bsodium.cron.worker.AiTurnWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toJavaLocalDate
import java.time.LocalDate as JavaLocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO

data class HomeUiState(
    val sessionDisplay: SessionDisplayState? = null,
    val greetingPrefix: String = "Welcome",
    val greetingName: String? = null,
    val dateLabel: String = "",
    val sleepStats: SleepStatsUi? = null,
    val aiPlan: AiPlanUi? = null,
    val isRetrying: Boolean = false,
    /** False until the backing flows have produced their first value — gates the onboarding so it
     *  doesn't flash over an existing plan during the cold-start load. */
    val initialized: Boolean = false,
    /** A plan-affecting setting changed since the last plan was written — offers a re-run. */
    val settingsChangedSincePlan: Boolean = false,
    /** The latest AI turn failed (and hasn't been dismissed) — surfaces a dismissible banner. */
    val aiFailure: AiTurnFailure? = null,
    /** User preference: fire subtle haptic ticks while the assistant streams. */
    val hapticsEnabled: Boolean = true,
    /** User preference: auto-plan and arm alarms each night. Off cancels everything armed. */
    val autoAlarmsEnabled: Boolean = true,
    /** The local time the nightly planning run fires — drives the resting screen's "next plan at …" line. */
    val eveningTriggerTime: LocalTime = LocalTime(20, 0),
)

/** Why the most recent AI turn ended without updating the plan, for the home failure banner. */
sealed interface AiTurnFailure {
    data class BudgetExhausted(val used: Int, val limit: Int) : AiTurnFailure
    data object MissingApiKey : AiTurnFailure
    data class Generic(val reason: String?) : AiTurnFailure
}

data class SleepStatsUi(
    val durationLabel: String,
    val segments: List<SleepSegment>,
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val db = CronDatabase.get(application)
    private val repository = SessionRepository(application)
    private val settings = SettingsRepository(application)
    private val locationProvider = LocationProvider(application)
    private val eveningPlanScheduler = EveningPlanScheduler(application)
    private val alarmScheduler = AlarmScheduler(application)
    private val hardLatestScheduler = HardLatestScheduler(application)

    private val _isRetrying = MutableStateFlow(false)

    /** The (sessionId, turnIndex) of the placeholder seeded into [StreamingTurnStore] when a user triggers
     *  a replan, so the optimistic tab IS the real turn. Tracked only to clear it if the worker dies before
     *  streaming (the normal path is cleared by TurnRunner's finally). Null when no replan is pending. */
    private val _optimisticTurn = MutableStateFlow<Pair<String, Int>?>(null)

    /** Failure parsed from the latest FAILED turn; null while idle, running, or once dismissed. */
    private val _aiFailure = MutableStateFlow<AiTurnFailure?>(null)

    /** id of the most-recent FAILED turn surfaced, and the one the user dismissed — WorkManager keeps
     *  re-emitting a terminal FAILED WorkInfo, so dismissal keys on its id to avoid resurrection. A new
     *  enqueue (REPLACE) gets a fresh id, so a genuinely new failure still shows. */
    private val _lastFailedId = MutableStateFlow<String?>(null)
    private val _dismissedFailureId = MutableStateFlow<String?>(null)

    /** Latest session entity; many derived streams hang off this. */
    private val sessionFlow = db.sessionDao().observeLatest()

    /** Sleep events for the active session — drives the timeline + duration. */
    private val sleepStatsFlow = sessionFlow
        .map { it?.id }
        .distinctUntilChanged()
        .flatMapLatest { id ->
            if (id == null) flowOf(null)
            else db.eventDao().observeBySession(id).map { events -> buildSleepStats(events) }
        }

    /** Latest-turn AI thread — the live streaming partial overrides the settled DB state while a turn
     *  for this session is in flight, so tokens render as they arrive. `.conflate()` bounds recomposition. */
    private val aiPlanFlow = sessionFlow
        .map { it?.id }
        .distinctUntilChanged()
        .flatMapLatest { id ->
            if (id == null) {
                flowOf(null)
            } else {
                combine(
                    db.aiMessageDao().observeBySession(id),
                    db.eventDao().observeBySession(id),
                    StreamingTurnStore.active,
                ) { rows, events, streaming ->
                    AiPlanMapper.buildPlan(rows, streaming?.takeIf { it.sessionId == id }, events.map { it.toModel() })
                }.conflate()
            }
        }

    /** Epoch-ms the user last dismissed the "settings changed" reminder this process. */
    private val _dismissedSettingsAt = MutableStateFlow(0L)

    /** True when a plan-affecting setting changed after the last AI call and the reminder hasn't
     *  been dismissed since. Gated on an existing plan at the combine site. */
    private val settingsChangedFlow = combine(
        sessionFlow.map { it?.lastAiCallAt ?: 0L },
        settings.settingsUpdatedAt,
        _dismissedSettingsAt,
    ) { lastAiCallAt, settingsAt, dismissedAt ->
        settingsAt > lastAiCallAt && settingsAt > dismissedAt
    }

    /** True while a turn for the active session is actively streaming — a real token-flow signal that
     *  drives the spinner, rather than waiting on WorkManager's coarser terminal-state transition. */
    private val streamingActiveFlow = combine(
        sessionFlow.map { it?.id }.distinctUntilChanged(),
        StreamingTurnStore.active,
    ) { id, streaming -> id != null && streaming?.sessionId == id }

    // UX prefs pre-combined so statusFlow stays within combine's 5-arg arity.
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

    val uiState: StateFlow<HomeUiState> = combine(
        sessionFlow.map { it?.toDisplayState() },
        sleepStatsFlow,
        aiPlanFlow,
        settings.displayName,
        statusFlow,
    ) { session, sleepStats, plan, displayName, status ->
        HomeUiState(
            sessionDisplay = session,
            greetingPrefix = greetingPrefix(),
            greetingName = displayName,
            dateLabel = formatDateLabel(session),
            sleepStats = sleepStats,
            aiPlan = plan,
            isRetrying = status.isRetrying,
            // combine() only emits once every source has produced a value, so reaching here means
            // the backing flows have loaded — distinguishes "no plan" from "not loaded yet".
            initialized = true,
            settingsChangedSincePlan = status.settingsChanged && plan != null,
            aiFailure = status.failure,
            hapticsEnabled = status.hapticsEnabled,
            autoAlarmsEnabled = status.autoAlarmsEnabled,
            eveningTriggerTime = status.eveningTriggerTime,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

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
                // Re-arm what the current session already decided — toggling ON must make Android aware
                // of the alarm again (symmetry with the OFF teardown below). The clock pref alone did nothing.
                repository.findCurrent()?.let { session ->
                    val tz = TimeZone.of(session.timezone)
                    hardLatestScheduler.arm(session.plan.hardLatest, session.date, tz, session.id)
                    val instr = session.currentInstruction
                    val alarmTime = instr.alarmTime
                    if (instr.action == ActionType.SetAlarm && alarmTime != null) {
                        val requested = LocalDateTime(
                            session.date.year, session.date.monthNumber, session.date.dayOfMonth,
                            alarmTime.hour, alarmTime.minute, alarmTime.second, alarmTime.nanosecond,
                        ).toInstant(tz)
                        alarmScheduler.schedule(
                            requested = requested,
                            hardLatest = session.plan.hardLatest,
                            sessionDate = session.date,
                            timezone = tz,
                            label = instr.reason.ifBlank { "Cron Alarm" },
                            sessionId = session.id,
                        )
                    }
                }
            } else {
                eveningPlanScheduler.cancel()
                repository.findCurrent()?.let { session ->
                    alarmScheduler.cancel(session.date)
                    hardLatestScheduler.clear(session.date)
                    repository.cancelAiTurn(session.id)
                }
            }
        }
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
    init {
        // Drive the "working" flag and the failure banner off the real WorkManager turn, not a fixed
        // delay: a tap sets isRetrying true optimistically; this clears it when the turn reaches a
        // terminal state, and surfaces the failure reason a FAILED turn carries in its output data.
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

    /** Hide the AI failure banner until the next distinct turn fails (keyed on the failed work's id). */
    fun dismissAiFailure() {
        _dismissedFailureId.value = _lastFailedId.value
        _aiFailure.value = null
    }

    fun retryAiPlan() {
        _isRetrying.value = true
        viewModelScope.launch(Dispatchers.IO) {
            val tz = TimeZone.currentSystemDefault()
            val morning = SessionRepository.morningDate(Clock.System.now(), tz)
            // A same-morning session means this is a replan of an existing plan, not a fresh bootstrap.
            val replanSession = repository.findCurrent()?.takeIf { it.date == morning }
            // Seed the upcoming turn FIRST (fast DB read) so its tab shows the instant the user taps —
            // before the slow location fetch — and IS the real turn (the worker uses maxTurn+1 too, and
            // appendEvent below adds an event, not an AiMessage, so the index stays correct).
            if (replanSession != null) {
                val nextTurn = (db.aiMessageDao().maxTurnIndex(replanSession.id) ?: -1) + 1
                // Seed with the trigger we're about to fire, so the new tab reads "Re-planned" immediately
                // instead of inheriting the prior event's label until our event is persisted.
                StreamingTurnStore.seedPending(replanSession.id, nextTurn, Clock.System.now().toEpochMilliseconds(), TriggerType.EveningPlan)
                _optimisticTurn.value = replanSession.id to nextTurn
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
                // Same-morning replan: append the fresh evening-plan event (latest wins in the worker),
                // pull in any plan-affecting setting changed since bootstrap, then re-run the turn.
                repository.appendEvent(replanSession.id, event)
                fsm.refreshPlanFromSettings(replanSession.id)
                repository.triggerAiTurn(replanSession.id)
            } else {
                // No session, or a stale one targeting an earlier morning (e.g. a morning re-run of a
                // session created last night) — let the FSM supersede the stale one and bootstrap a
                // fresh session for the correct upcoming morning, so the alarm isn't pinned to today.
                fsm.onEvent(event)
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

    /** Drops the seeded placeholder turn if the worker never streamed it (the normal path is cleared by
     *  TurnRunner). Safe to call always: if the turn streamed/settled, the active entry no longer matches. */
    private fun clearOptimisticTurn() {
        _optimisticTurn.value?.let { (session, turn) -> StreamingTurnStore.clear(session, turn) }
        _optimisticTurn.value = null
    }

    private fun SessionEntity.toDisplayState(): SessionDisplayState? {
        val instruction = runCatching {
            SessionJson.decodeFromString<Instruction>(currentInstructionJson)
        }
            .onFailure { Log.w(TAG, "decode instruction failed for session $id", it) }
            .getOrNull() ?: return null
        val sessionDate = runCatching { LocalDate.parse(date) }
            .onFailure { Log.w(TAG, "parse session date failed for session $id", it) }
            .getOrNull() ?: return null
        return SessionDisplayState(
            status = runCatching { SessionStatus.valueOf(status) }
                .onFailure { Log.w(TAG, "unknown session status '$status' for $id", it) }
                .getOrElse { SessionStatus.Complete },
            action = instruction.action,
            alarmTime = instruction.alarmTime,
            reason = instruction.reason,
            sessionDate = sessionDate,
            snoozeCount = snoozeCount,
        )
    }

    private fun buildSleepStats(rows: List<SessionEventEntity>): SleepStatsUi? {
        val segments = rows
            .filter { it.trigger == TriggerType.HcStageUpdate.name }
            .mapNotNull { row ->
                val data = runCatching {
                    SessionJson.decodeFromString<EventData>(row.dataJson)
                }
                    .onFailure { Log.w(TAG, "decode HcStageUpdate failed for event ${row.id}", it) }
                    .getOrNull() as? EventData.HcStageUpdate ?: return@mapNotNull null
                SleepSegment(
                    stage = data.stage,
                    start = data.recordStart,
                    end = data.recordEnd,
                )
            }
            .sortedBy { it.start }
        if (segments.isEmpty()) return null
        val totalSpan = segments.last().end - segments.first().start
        return SleepStatsUi(
            durationLabel = formatDuration(totalSpan),
            segments = segments,
        )
    }

    /** Maps an [AiTurnWorker] failure's output data to a UI failure. Unknown/absent reasons (open input
     *  — future worker reasons) fall through to [AiTurnFailure.Generic]. */
    private fun Data.toAiTurnFailure(): AiTurnFailure = when (getString(AiTurnWorker.KEY_REASON)) {
        AiTurnWorker.REASON_BUDGET -> AiTurnFailure.BudgetExhausted(
            used = getInt(AiTurnWorker.KEY_USED, 0),
            limit = getInt(AiTurnWorker.KEY_LIMIT, 0),
        )
        AiTurnWorker.REASON_NO_API_KEY -> AiTurnFailure.MissingApiKey
        else -> AiTurnFailure.Generic(getString(AiTurnWorker.KEY_REASON))
    }

    /** The card header reads the date the alarm fires (the session's morning), falling back to today
     *  when idle. Relative when near — "Today" / "Tomorrow" — else "EEEE d" (e.g. "Tuesday 2");
     *  locale-default for the human-language weekday name. */
    private fun formatDateLabel(session: SessionDisplayState?): String {
        val today = JavaLocalDate.now()
        val date = session?.sessionDate?.toJavaLocalDate() ?: today
        return when (ChronoUnit.DAYS.between(today, date)) {
            0L -> "Today"
            1L -> "Tomorrow"
            else -> date.format(DateTimeFormatter.ofPattern("EEEE d", Locale.getDefault()))
        }
    }

    private fun formatDuration(d: Duration): String {
        if (d <= ZERO) return "0H 0M"
        val total = d.inWholeMinutes
        val h = total / 60
        val m = total % 60
        return "${h}H ${m}M"
    }

    private companion object {
        const val TAG = "HomeViewModel"
    }
}

/** The transient status signals folded into one combine source to keep uiState's arity at 5. */
private data class HomeStatus(
    val isRetrying: Boolean,
    val settingsChanged: Boolean,
    val failure: AiTurnFailure?,
    val hapticsEnabled: Boolean,
    val autoAlarmsEnabled: Boolean,
    val eveningTriggerTime: LocalTime,
)

/** The DataStore-backed UX prefs, pre-combined so [HomeStatus] stays within combine's arity. */
private data class HomePrefs(
    val hapticsEnabled: Boolean,
    val autoAlarmsEnabled: Boolean,
    val eveningTriggerTime: LocalTime,
)
