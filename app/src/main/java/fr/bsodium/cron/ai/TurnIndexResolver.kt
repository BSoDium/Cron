package fr.bsodium.cron.ai

import android.util.Log
import fr.bsodium.cron.ai.wire.ContentBlock
import fr.bsodium.cron.session.db.AiMessageDao
import fr.bsodium.cron.session.db.AiMessageEntity
import fr.bsodium.cron.session.db.SessionJson

/**
 * Picks the turn index an AI-turn worker attempt should run.
 *
 * A first attempt always starts a fresh turn after the highest persisted index. A retry whose
 * failed predecessor left partial rows resumes that turn instead — [TurnRunner.loadOrSeed] picks
 * the persisted messages back up — so round-trips that already billed aren't re-run and
 * side-effectful tools (alarms, notifications) aren't re-executed. First attempts never resume:
 * a REPLACE'd worker's partial turn carries a stale prompt, and the new trigger wants a new turn.
 */
object TurnIndexResolver {

    suspend fun resolve(dao: AiMessageDao, sessionId: String, isRetry: Boolean): Int {
        val maxIndex = dao.maxTurnIndex(sessionId) ?: return 0
        if (isRetry && !isSettled(dao.findByTurn(sessionId, maxIndex))) return maxIndex
        return maxIndex + 1
    }

    /** A turn is settled once its last row is an assistant message with no pending tool_use. */
    private fun isSettled(rows: List<AiMessageEntity>): Boolean {
        val last = rows.lastOrNull() ?: return true
        if (last.role != "assistant") return false
        // An undecodable row can't be resumed either (loadOrSeed would throw on it) — start fresh.
        val blocks = runCatching { SessionJson.decodeFromString<List<ContentBlock>>(last.contentJson) }
            .onFailure { Log.w(TAG, "Undecodable row ${last.id} in turn ${last.turnIndex}", it) }
            .getOrNull() ?: return true
        return blocks.none { it is ContentBlock.ToolUse }
    }
}

private const val TAG = "TurnIndexResolver"
