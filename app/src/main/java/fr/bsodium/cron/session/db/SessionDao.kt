package fr.bsodium.cron.session.db

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(session: SessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(session: SessionEntity)

    @Update
    suspend fun update(session: SessionEntity)

    // Targeted single-column UPDATEs so a concurrent writer to a different column can't be clobbered by a stale full-row update(entity) (#153).

    @Query("UPDATE sessions SET status = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, updatedAt: Long)

    @Query("UPDATE sessions SET planJson = :planJson, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updatePlan(id: String, planJson: String, updatedAt: Long)

    @Query("UPDATE sessions SET lastAiCallAt = :lastAiCallAt, updatedAt = :updatedAt WHERE id = :id")
    suspend fun markAiTriggered(id: String, lastAiCallAt: Long, updatedAt: Long)

    @Query(
        "UPDATE sessions SET currentInstructionJson = :instructionJson, lastAiCallAt = :lastAiCallAt, " +
            "updatedAt = :updatedAt WHERE id = :id",
    )
    suspend fun updateInstruction(id: String, instructionJson: String, lastAiCallAt: Long, updatedAt: Long)

    // Atomic increment, not read-then-write, so two near-simultaneous snoozes both land.
    @Query("UPDATE sessions SET snoozeCount = snoozeCount + 1, updatedAt = :updatedAt WHERE id = :id")
    suspend fun incrementSnoozeCount(id: String, updatedAt: Long)

    @Query("SELECT snoozeCount FROM sessions WHERE id = :id")
    suspend fun getSnoozeCount(id: String): Int?

    @Query("UPDATE sessions SET cachedFirstEventSig = :sig, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateCachedFirstEventSig(id: String, sig: String?, updatedAt: Long)

    @Query("SELECT * FROM sessions WHERE date = :date LIMIT 1")
    suspend fun findByDate(date: String): SessionEntity?

    @Query("SELECT * FROM sessions WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): SessionEntity?

    @Query("SELECT * FROM sessions WHERE status != 'Complete' ORDER BY createdAt DESC LIMIT 1")
    suspend fun findCurrent(): SessionEntity?

    /** Backs [fr.bsodium.cron.ui.screens.home.TimelineRepository.mostRecentOlderAlarmTime]'s
     *  carry-over-alarm-time patch (#230) — the caller walks these, most-recent-first, until one
     *  actually resolved an alarm (a single most-recent row isn't enough: that one session alone might
     *  never have resolved one — cancelled, auto-alarms off, an errored turn). */
    @Query("SELECT * FROM sessions WHERE (:excludeSessionId IS NULL OR id != :excludeSessionId) ORDER BY createdAt DESC LIMIT :limit")
    suspend fun findRecentExcluding(excludeSessionId: String?, limit: Int): List<SessionEntity>

    @Query("SELECT * FROM sessions ORDER BY createdAt DESC LIMIT 1")
    fun observeLatest(): Flow<SessionEntity?>

    @Query("DELETE FROM sessions WHERE createdAt < :olderThanMillis")
    suspend fun deleteOlderThan(olderThanMillis: Long): Int

    /** Backs Home's paged history feed (#187) — `excludeSessionId` keeps the live/current session out
     *  of the historical Pager entirely, so it never double-renders alongside the reactive live-session
     *  path. */
    @Query("SELECT * FROM sessions WHERE (:excludeSessionId IS NULL OR id != :excludeSessionId) ORDER BY createdAt DESC")
    fun historyPagingSource(excludeSessionId: String?): PagingSource<Int, SessionEntity>

    @Query("DELETE FROM sessions")
    suspend fun deleteAll(): Int
}
