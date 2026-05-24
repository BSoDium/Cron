package fr.bsodium.cron.ui.screens.home

import android.app.AlarmManager
import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.bsodium.cron.ai.SmokeTest
import fr.bsodium.cron.receiver.AlarmReceiver
import fr.bsodium.cron.sensors.DebugSensorEventSink
import fr.bsodium.cron.service.SleepSessionService
import fr.bsodium.cron.session.db.CronDatabase
import fr.bsodium.cron.session.db.SessionEntity
import fr.bsodium.cron.session.db.SessionJson
import fr.bsodium.cron.session.model.Instruction
import fr.bsodium.cron.session.model.SessionEvent
import fr.bsodium.cron.session.model.SessionStatus
import fr.bsodium.cron.settings.SecureKeyStore
import fr.bsodium.cron.ui.components.SessionDisplayState
import fr.bsodium.cron.ui.screens.alarm.AlarmActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate

data class HomeUiState(
    val sessionDisplay: SessionDisplayState? = null,
    val hasAnthropicKey: Boolean = false,
    val smokeState: SmokeState = SmokeState.Idle,
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

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val db = CronDatabase.get(application)
    private val secureStore = SecureKeyStore(application)

    private val _smokeState = MutableStateFlow<SmokeState>(SmokeState.Idle)
    private val _hasApiKey = MutableStateFlow(secureStore.hasAnthropicKey())

    val uiState: StateFlow<HomeUiState> = combine(
        db.sessionDao().observeLatest().map { it?.toDisplayState() },
        _smokeState,
        _hasApiKey,
    ) { session, smoke, hasKey ->
        HomeUiState(sessionDisplay = session, smokeState = smoke, hasAnthropicKey = hasKey)
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

    companion object {
        private const val TEST_ALARM_REQUEST_CODE = 999_999
    }
}
