package fr.bsodium.cron.ui.screens.home

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Data
import androidx.work.WorkInfo
import fr.bsodium.cron.ai.wire.ContentBlock
import fr.bsodium.cron.calendar.requestCalendarSync
import fr.bsodium.cron.location.LocationProvider
import fr.bsodium.cron.session.SessionFsm
import fr.bsodium.cron.session.SessionRepository
import fr.bsodium.cron.session.db.AiMessageEntity
import fr.bsodium.cron.session.db.CronDatabase
import fr.bsodium.cron.session.db.SessionEntity
import fr.bsodium.cron.session.db.SessionEventEntity
import fr.bsodium.cron.session.db.SessionJson
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDate as JavaLocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO

data class HomeUiState(
    val sessionDisplay: SessionDisplayState? = null,
    val greetingPrefix: String = "Welcome",
    val greetingName: String? = null,
    val dateLabel: String = "",
    val sleepStats: SleepStatsUi? = null,
    val aiThread: AiThreadUi? = null,
    val isRetrying: Boolean = false,
    /** False until the backing flows have produced their first value — gates the onboarding so it
     *  doesn't flash over an existing plan during the cold-start load. */
    val initialized: Boolean = false,
    /** A plan-affecting setting changed since the last plan was written — offers a re-run. */
    val settingsChangedSincePlan: Boolean = false,
    /** The latest AI turn failed (and hasn't been dismissed) — surfaces a dismissible banner. */
    val aiFailure: AiTurnFailure? = null,
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

data class AiThreadUi(
    val turnIndex: Int,
    val summary: String?,
    val process: List<ProcessItem>,
    val response: String?,
    /** Wall-clock seconds the turn took — shown as "Thought for Xs" once settled. */
    val durationSeconds: Int? = null,
)

/** One ordered step of the assistant's thinking process, shown inside the collapsible. */
sealed interface ProcessItem {
    data class Reasoning(val text: String) : ProcessItem
    data class Narration(val text: String) : ProcessItem
    data class Tool(
        val name: String,
        val isComplete: Boolean,
        /** Short result summary (e.g. "12 events"), shown in place of a checkmark. */
        val contextLabel: String? = null,
        /** The tool returned an error result — shown as a warning glyph instead of a check. */
        val isError: Boolean = false,
    ) : ProcessItem
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val db = CronDatabase.get(application)
    private val repository = SessionRepository(application)
    private val settings = SettingsRepository(application)
    private val locationProvider = LocationProvider(application)

    private val _isRetrying = MutableStateFlow(false)

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

    /** Latest-turn AI message blocks — drives the thinking thread. */
    private val aiThreadFlow = sessionFlow
        .map { it?.id }
        .distinctUntilChanged()
        .flatMapLatest { id ->
            if (id == null) flowOf(null)
            else db.aiMessageDao().observeBySession(id).map { rows -> buildAiThread(rows) }
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

    private val statusFlow = combine(
        _isRetrying,
        settingsChangedFlow,
        _aiFailure,
    ) { retrying, settingsChanged, failure ->
        HomeStatus(isRetrying = retrying, settingsChanged = settingsChanged, failure = failure)
    }

    val uiState: StateFlow<HomeUiState> = combine(
        sessionFlow.map { it?.toDisplayState() },
        sleepStatsFlow,
        aiThreadFlow,
        settings.displayName,
        statusFlow,
    ) { session, sleepStats, thread, displayName, status ->
        HomeUiState(
            sessionDisplay = session,
            greetingPrefix = greetingPrefix(),
            greetingName = displayName,
            dateLabel = formatDateLabel(session),
            sleepStats = sleepStats,
            aiThread = thread,
            isRetrying = status.isRetrying,
            // combine() only emits once every source has produced a value, so reaching here means
            // the backing flows have loaded — distinguishes "no plan" from "not loaded yet".
            initialized = true,
            settingsChangedSincePlan = status.settingsChanged && thread != null,
            aiFailure = status.failure,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    /** Hide the "settings changed" reminder until the next plan-affecting change. */
    fun dismissSettingsReminder() {
        _dismissedSettingsAt.value = Clock.System.now().toEpochMilliseconds()
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
                        infos.isNotEmpty() -> _isRetrying.value = false
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
            // Nudge a calendar sync up front; the location fetch + AI round-trip below give it time to land.
            requestCalendarSync()
            // Manual replan captures a FRESH foreground fix — the user may have moved since the session began.
            val tz = TimeZone.currentSystemDefault()
            val location = locationProvider.acquireForEveningPlan()
            val event = SessionEvent(
                trigger = TriggerType.EveningPlan,
                timestamp = Clock.System.now(),
                data = EventData.EveningPlan(timezone = tz.id, location = location),
            )
            val fsm = SessionFsm(getApplication(), repository)
            val current = db.sessionDao().findCurrent()
            if (current != null) {
                // Append the fresh evening-plan event (latest wins in the worker), pull in any
                // plan-affecting setting changed since bootstrap, then re-run the turn.
                repository.appendEvent(current.id, event)
                fsm.refreshPlanFromSettings(current.id)
                repository.triggerAiTurn(current.id)
            } else {
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

    private fun buildAiThread(rows: List<AiMessageEntity>): AiThreadUi? {
        if (rows.isEmpty()) return null
        val latestTurn = rows.maxOf { it.turnIndex }
        val assistantRows = rows.filter { it.turnIndex == latestTurn && it.role == "assistant" }
        val userRows = rows.filter { it.turnIndex == latestTurn && it.role == "user" }
        if (assistantRows.isEmpty()) return AiThreadUi(
            turnIndex = latestTurn,
            summary = "Thinking…",
            process = emptyList(),
            response = null,
        )

        val blocks = assistantRows.flatMap { decodeBlocks(it.contentJson) }
        val toolResults = userRows
            .flatMap { decodeBlocks(it.contentJson) }
            .filterIsInstance<ContentBlock.ToolResult>()
            .associateBy { it.tool_use_id }

        // Model-authored pill labels: "STATUS: <gerund>" while working, leading "SUMMARY: <past>" on
        // the answer. Pull them out in order and strip them so they never render.
        val statuses = mutableListOf<String>()
        var summaryLine: String? = null
        fun stripDirectives(text: String): String {
            val kept = StringBuilder()
            text.lineSequence().forEach { line ->
                val trimmed = line.trim()
                val status = STATUS_LINE.matchEntire(trimmed)
                val summary = SUMMARY_LINE.matchEntire(trimmed)
                when {
                    status != null -> status.groupValues[1].trim().takeIf { it.isNotEmpty() }?.let { statuses += it }
                    summary != null -> summary.groupValues[1].trim().takeIf { it.isNotEmpty() }?.let { summaryLine = it }
                    else -> kept.appendLine(line)
                }
            }
            return kept.toString().trim()
        }

        // The answer is the trailing run of Text blocks (after the last tool/reasoning block); text
        // emitted earlier is thinking-process narration, not output.
        val answerStart = blocks.indexOfLast { it !is ContentBlock.Text } + 1

        val process = blocks.take(answerStart).mapNotNull { block ->
            when (block) {
                is ContentBlock.Thinking ->
                    block.thinking.takeIf { it.isNotBlank() }?.let { ProcessItem.Reasoning(it) }
                is ContentBlock.Text ->
                    stripDirectives(block.text).takeIf { it.isNotBlank() }?.let { ProcessItem.Narration(it) }
                is ContentBlock.ToolUse -> {
                    val result = toolResults[block.id]
                    ProcessItem.Tool(
                        name = block.name,
                        isComplete = result != null,
                        contextLabel = result
                            ?.takeIf { it.is_error != true }
                            ?.let { summarizeToolResult(block.name, it.content) },
                        isError = result?.is_error == true,
                    )
                }
                is ContentBlock.ToolResult -> null
            }
        }

        val response = blocks.drop(answerStart)
            .filterIsInstance<ContentBlock.Text>()
            .joinToString(separator = "\n\n") { it.text }
            .let(::stripDirectives)
            .let(::stripLeadingRule)
            .takeIf { it.isNotBlank() }

        // A do_nothing turn ends with no trailing text; fall back to the SUMMARY line, then the
        // do_nothing reason, so the card still explains the decision.
        val doNothingReason = blocks
            .filterIsInstance<ContentBlock.ToolUse>()
            .firstOrNull { it.name == "do_nothing" }
            ?.let { tool ->
                runCatching { tool.input.jsonObject["reason"]?.jsonPrimitive?.content }
                    .onFailure { Log.w(TAG, "read do_nothing reason failed", it) }
                    .getOrNull()
            }
            ?.takeIf { it.isNotBlank() }
        val answer = response ?: summaryLine ?: doNothingReason

        // Wall-clock span of the latest turn; shown once the turn settles (UI drives this off WorkManager).
        val turnRows = rows.filter { it.turnIndex == latestTurn }
        val durationSeconds = if (turnRows.isNotEmpty()) {
            ((turnRows.maxOf { it.createdAt } - turnRows.minOf { it.createdAt }) / 1000).toInt()
        } else {
            null
        }

        // Pill preview: gerund while working, past-tense summary once answered; falls back to the
        // first reasoning line if the model skipped the directives.
        val fallback = process
            .firstNotNullOfOrNull { (it as? ProcessItem.Reasoning)?.text ?: (it as? ProcessItem.Narration)?.text }
            ?.lineSequence()
            ?.firstOrNull { it.isNotBlank() }
            ?.take(80)
        val liveStatus = statuses.lastOrNull()
        val summary =
            if (answer != null) summaryLine ?: liveStatus ?: fallback
            else liveStatus ?: summaryLine ?: fallback

        return AiThreadUi(
            turnIndex = latestTurn,
            summary = summary,
            process = process,
            response = answer,
            durationSeconds = durationSeconds,
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

    private fun decodeBlocks(json: String): List<ContentBlock> = runCatching {
        SessionJson.decodeFromString<List<ContentBlock>>(json)
    }
        .onFailure { Log.w(TAG, "decode AI content blocks failed", it) }
        .getOrElse { emptyList() }

    /** Drop a leading thematic break (e.g. "---") the model sometimes prefixes the answer with. */
    private fun stripLeadingRule(text: String): String {
        val lines = text.trimStart().lines()
        val first = lines.firstOrNull()?.trim().orEmpty()
        return if (first.matches(LEADING_RULE)) {
            lines.drop(1).joinToString("\n").trimStart()
        } else {
            text
        }
    }

    /** Condense a tool's JSON result into a one-line status label, or null if not worth showing. */
    private fun summarizeToolResult(name: String, content: String): String? = runCatching {
        val obj = SessionJson.parseToJsonElement(content).jsonObject
        when (name) {
            "read_calendar" -> {
                val n = obj["events"]?.jsonArray?.size ?: 0
                if (n == 1) "1 event" else "$n events"
            }
            "set_alarm" -> obj["alarm_time"]?.jsonPrimitive?.content?.let { iso ->
                val local = Instant.parse(iso).toLocalDateTime(TimeZone.currentSystemDefault())
                String.format(Locale.US, "set for %02d:%02d", local.hour, local.minute)
            }
            "cancel_alarm" -> "cancelled"
            "estimate_commute" -> obj["duration_sec"]?.jsonPrimitive?.content?.toLongOrNull()
                ?.let { "${it / 60} min" }
            "geocode_address" -> obj["formatted"]?.jsonPrimitive?.content?.take(28)
            else -> null
        }
    }.onFailure { Log.w(TAG, "summarize tool result failed for $name", it) }.getOrNull()

    /** The card header reads the date the alarm fires (the session's morning), falling back to today
     *  when idle. Format "EEEE d" (e.g. "Tuesday 2"); locale-default for human-language weekday names. */
    private fun formatDateLabel(session: SessionDisplayState?): String {
        val date = session?.sessionDate?.toJavaLocalDate() ?: JavaLocalDate.now()
        return date.format(DateTimeFormatter.ofPattern("EEEE d", Locale.getDefault()))
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

/** The three transient status signals folded into one combine source to keep uiState's arity at 5. */
private data class HomeStatus(
    val isRetrying: Boolean,
    val settingsChanged: Boolean,
    val failure: AiTurnFailure?,
)

// Hoisted so they compile once, not on every buildAiThread call.
private val LEADING_RULE = Regex("([-*_])\\1{2,}")
private val STATUS_LINE = Regex("(?i)^STATUS:\\s*(.*)$")
private val SUMMARY_LINE = Regex("(?i)^SUMMARY:\\s*(.*)$")
