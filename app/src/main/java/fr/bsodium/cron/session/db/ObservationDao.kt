package fr.bsodium.cron.session.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ObservationDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(observation: ObservationEntity): Long

    // ORDER BY timestamp, not id: independent fire-and-forget writers from different monitors/
    // dispatchers can commit out of chronological order for near-simultaneous events.
    @Query("SELECT * FROM observation_log WHERE sessionId = :sessionId ORDER BY timestamp ASC, id ASC")
    suspend fun findBySession(sessionId: String): List<ObservationEntity>

    @Query("DELETE FROM observation_log")
    suspend fun deleteAll(): Int
}
