package fr.bsodium.cron.ui.screens.home

import fr.bsodium.cron.session.db.AiMessageEntity

/**
 * Per-session memo for [AiPlanMapper.buildPlan]: a settled turn's rows are immutable, so its
 * [AiThreadUi] is rebuilt only when the turn's rows change (keyed by their ids). Caps the per-emission
 * cost at the streaming/changed turn instead of re-decoding every turn's JSON. One instance per session,
 * confined to a single Default-dispatched flow, so a plain map needs no synchronization.
 */
internal data class HomeDisplay(
    val session: SessionDisplayState?,
    val displayName: String?,
)

internal data class HomeStatus(
    val isRetrying: Boolean,
    val settingsChanged: Boolean,
    val failure: AiTurnFailure?,
    val hapticsEnabled: Boolean,
    val autoAlarmsEnabled: Boolean,
    val eveningTriggerTime: kotlinx.datetime.LocalTime,
)

internal data class HomePrefs(
    val hapticsEnabled: Boolean,
    val autoAlarmsEnabled: Boolean,
    val eveningTriggerTime: kotlinx.datetime.LocalTime,
)

internal class TurnThreadCache {
    private val cache = HashMap<Int, Pair<List<Long>, AiThreadUi>>()

    fun threadFor(turn: Int, rows: List<AiMessageEntity>): AiThreadUi {
        val signature = rows.map { it.id }
        cache[turn]?.let { (sig, thread) -> if (sig == signature) return thread }
        val thread = AiThreadMapper.build(rows) ?: AiThreadUi(turn, summary = null, process = emptyList(), response = null)
        cache[turn] = signature to thread
        return thread
    }
}
