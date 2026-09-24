package fr.bsodium.cron.session.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ObservationDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(observation: ObservationEntity): Long

    @Query("SELECT * FROM observation_log WHERE sessionId = :sessionId ORDER BY id ASC")
    suspend fun findBySession(sessionId: String): List<ObservationEntity>

    @Query("DELETE FROM observation_log")
    suspend fun deleteAll(): Int
}
