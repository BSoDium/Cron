package fr.bsodium.cron.session.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(event: SessionEventEntity): Long

    @Query("SELECT * FROM session_events WHERE sessionId = :sessionId ORDER BY id ASC")
    suspend fun findBySession(sessionId: String): List<SessionEventEntity>

    @Query("SELECT * FROM session_events WHERE sessionId = :sessionId ORDER BY id ASC")
    fun observeBySession(sessionId: String): Flow<List<SessionEventEntity>>

    @Query("SELECT * FROM session_events WHERE sessionId = :sessionId AND trigger = :trigger ORDER BY id DESC LIMIT 1")
    suspend fun findLatestByTrigger(sessionId: String, trigger: String): SessionEventEntity?
}
