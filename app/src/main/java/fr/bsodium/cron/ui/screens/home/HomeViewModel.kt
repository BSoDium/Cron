package fr.bsodium.cron.ui.screens.home

import android.app.AlarmManager
import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.bsodium.cron.ai.SmokeTest
import fr.bsodium.cron.ai.wire.ContentBlock
import fr.bsodium.cron.receiver.AlarmReceiver
import fr.bsodium.cron.sensors.DebugSensorEventSink
import fr.bsodium.cron.service.SleepSessionService
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
import fr.bsodium.cron.settings.SecureKeyStore
import fr.bsodium.cron.ui.components.SessionDisplayState
import fr.bsodium.cron.ui.screens.alarm.AlarmActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import java.time.LocalDate as JavaLocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO

data class HomeUiState(
    val sessionDisplay: SessionDisplayState? = null,
    val hasAnthropicKey: Boolean = false,
    val smokeState: SmokeState = SmokeState.Idle,
    val greetingPrefix: String = "Welcome",
    val greetingName: String? = null,
    val dateLabel: String = "",
    val sleepStats: SleepStatsUi? = null,
    val aiThread: AiThreadUi? = null,
    val isRetrying: Boolean = false,
)

data class SleepStatsUi(
    val durationLabel: String,
    val segments: List<SleepSegment>,
)

data class AiThreadUi(
    val turnIndex: Int,
    val summary: String?,
    val thinking: String?,
    val toolSteps: List<ToolStep>,
    val response: String?,
    val isComplete: Boolean,
)

data class ToolStep(
    val name: String,
    val isComplete: Boolean,
)

sealed class SmokeState {
    data object Idle : SmokeState()
    data object Running : SmokeState()
    data class Success(
        val text: String,
        val roundTrips: Int,
        val originLat: Double? = null,
        val originLng: Double? = null,
        val destination: String? = null,
    ) : SmokeState()
    data class Failure(val message: String) : SmokeState()
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val db = CronDatabase.get(application)
    private val secureStore = SecureKeyStore(application)
    private val repository = SessionRepository(application)

    private val _smokeState = MutableStateFlow<SmokeState>(SmokeState.Idle)
    private val _hasApiKey = MutableStateFlow(secureStore.hasAnthropicKey())
    private val _isRetrying = MutableStateFlow(false)

    /** AccountManager lookup is synchronous IO — resolve once at VM creation. */
    private val greetingName: String? = resolveGreetingName(application)

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

    val uiState: StateFlow<HomeUiState> = combine(
        sessionFlow.map { it?.toDisplayState() },
        _smokeState,
        _hasApiKey,
        sleepStatsFlow,
        combine(aiThreadFlow, _isRetrying) { thread, retrying -> thread to retrying },
    ) { session, smoke, hasKey, sleepStats, threadAndRetry ->
        val (thread, retrying) = threadAndRetry
        HomeUiState(
            sessionDisplay = session,
            smokeState = smoke,
            hasAnthropicKey = hasKey,
            greetingPrefix = greetingPrefix(),
            greetingName = greetingName,
            dateLabel = formatDateLabel(),
            sleepStats = sleepStats,
            aiThread = thread,
            isRetrying = retrying,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    val recentSensorEvents: StateFlow<List<SessionEvent>> = DebugSensorEventSink.events
        .runningFold(emptyList<SessionEvent>()) { acc, ev -> (listOf(ev) + acc).take(20) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun saveAnthropicKey(key: String) {
        secureStore.anthropicApiKey = if (key.isBlank()) null else key
        _hasApiKey.value = secureStore.hasAnthropicKey()
        _smokeState.value = SmokeState.Idle
    }

    fun startSensorService() {
        val ctx = getApplication<Application>()
        ctx.startForegroundService(SleepSessionService.startIntent(ctx))
    }

    fun stopSensorService() {
        val ctx = getApplication<Application>()
        ctx.startService(SleepSessionService.stopIntent(ctx))
    }

    fun fireTestAlarm() {
        val ctx = getApplication<Application>()
        val triggerAt = System.currentTimeMillis() + 30_000L
        val intent = Intent(ctx, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_ALARM_FIRED
            putExtra(AlarmReceiver.EXTRA_REQUEST_CODE, TEST_ALARM_REQUEST_CODE)
            putExtra(AlarmReceiver.EXTRA_LABEL, "Test alarm")
            putExtra(AlarmReceiver.EXTRA_SNOOZE_COUNT, 0)
        }
        val pi = PendingIntent.getBroadcast(
            ctx, TEST_ALARM_REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
    }

    fun openAlarmScreen() {
        val ctx = getApplication<Application>()
        ctx.startActivity(
            Intent(ctx, AlarmActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                putExtra(AlarmReceiver.EXTRA_LABEL, "Preview alarm")
                putExtra(AlarmReceiver.EXTRA_REQUEST_CODE, TEST_ALARM_REQUEST_CODE)
                putExtra(AlarmReceiver.EXTRA_SNOOZE_COUNT, 0)
            }
        )
    }

    fun runSmokeTest() {
        _smokeState.value = SmokeState.Running
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                SmokeTest(getApplication()).run()
            }
            _smokeState.value = if (result.ok) {
                SmokeState.Success(
                    text = result.assistantText.orEmpty(),
                    roundTrips = result.roundTrips,
                    originLat = result.originLat,
                    originLng = result.originLng,
                    destination = result.destination,
                )
            } else {
                SmokeState.Failure(result.error ?: "unknown error")
            }
        }
    }

    /**
     * Re-runs the AI alarm prediction for the active session. No-op if no
     * session is active. Spins the retry button while the worker runs.
     */
    fun retryAiPlan() {
        viewModelScope.launch {
            val current = withContext(Dispatchers.IO) { db.sessionDao().findCurrent() }
            val id = current?.id ?: return@launch
            _isRetrying.value = true
            repository.triggerAiTurn(id)
            // No tight signal for "AI turn done" yet; spin briefly so the user sees
            // the tap registered. New messages will stream in via aiThreadFlow.
            kotlinx.coroutines.delay(2_500)
            _isRetrying.value = false
        }
    }

    private fun SessionEntity.toDisplayState(): SessionDisplayState? {
        val instruction = runCatching {
            SessionJson.decodeFromString<Instruction>(currentInstructionJson)
        }.getOrNull() ?: return null
        val sessionDate = runCatching { LocalDate.parse(date) }.getOrNull() ?: return null
        return SessionDisplayState(
            status = runCatching { SessionStatus.valueOf(status) }.getOrElse { SessionStatus.Complete },
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
                }.getOrNull() as? EventData.HcStageUpdate ?: return@mapNotNull null
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
            thinking = null,
            toolSteps = emptyList(),
            response = null,
            isComplete = false,
        )

        val allAssistantBlocks = assistantRows.flatMap { decodeBlocks(it.contentJson) }
        val toolResults = userRows
            .flatMap { decodeBlocks(it.contentJson) }
            .filterIsInstance<ContentBlock.ToolResult>()
            .associateBy { it.tool_use_id }

        val thinking = allAssistantBlocks
            .filterIsInstance<ContentBlock.Thinking>()
            .joinToString(separator = "\n\n") { it.thinking }
            .takeIf { it.isNotBlank() }
        val toolSteps = allAssistantBlocks
            .filterIsInstance<ContentBlock.ToolUse>()
            .map { use ->
                ToolStep(name = use.name, isComplete = toolResults.containsKey(use.id))
            }
        val response = allAssistantBlocks
            .filterIsInstance<ContentBlock.Text>()
            .joinToString(separator = "\n\n") { it.text }
            .takeIf { it.isNotBlank() }
        val summary = response
            ?.lineSequence()
            ?.firstOrNull { it.isNotBlank() }
            ?.take(80)
        val isComplete = response != null && allAssistantBlocks
            .filterIsInstance<ContentBlock.ToolUse>()
            .all { toolResults.containsKey(it.id) }

        return AiThreadUi(
            turnIndex = latestTurn,
            summary = summary,
            thinking = thinking,
            toolSteps = toolSteps,
            response = response,
            isComplete = isComplete,
        )
    }

    private fun decodeBlocks(json: String): List<ContentBlock> = runCatching {
        SessionJson.decodeFromString<List<ContentBlock>>(json)
    }.getOrElse { emptyList() }

    private fun formatDateLabel(): String {
        val today = JavaLocalDate.now()
        // Mockup shows "Tuesday 17"; en-US locale gives the day-of-week, numeric day.
        return today.format(DateTimeFormatter.ofPattern("EEEE d", Locale.getDefault()))
    }

    private fun formatDuration(d: Duration): String {
        if (d <= ZERO) return "0H 0M"
        val total = d.inWholeMinutes
        val h = total / 60
        val m = total % 60
        return "${h}H ${m}M"
    }

    companion object {
        private const val TEST_ALARM_REQUEST_CODE = 999_999
    }
}
