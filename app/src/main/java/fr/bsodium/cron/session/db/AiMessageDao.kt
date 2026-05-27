package fr.bsodium.cron.session.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AiMessageDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(message: AiMessageEntity): Long

    @Query(
        """
        SELECT * FROM ai_messages
        WHERE sessionId = :sessionId AND turnIndex = :turnIndex
        ORDER BY id ASC
        """
    )
    suspend fun findByTurn(sessionId: String, turnIndex: Int): List<AiMessageEntity>

    @Query("SELECT MAX(turnIndex) FROM ai_messages WHERE sessionId = :sessionId")
    suspend fun maxTurnIndex(sessionId: String): Int?

    @Query("SELECT * FROM ai_messages WHERE sessionId = :sessionId ORDER BY id ASC")
    suspend fun findBySession(sessionId: String): List<AiMessageEntity>

    @Query("SELECT * FROM ai_messages WHERE sessionId = :sessionId ORDER BY id ASC")
    fun observeBySession(sessionId: String): Flow<List<AiMessageEntity>>
}
