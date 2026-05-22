package fr.bsodium.cron.session.db

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

    @Query("SELECT * FROM sessions WHERE date = :date LIMIT 1")
    suspend fun findByDate(date: String): SessionEntity?

    @Query("SELECT * FROM sessions WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): SessionEntity?

    @Query("SELECT * FROM sessions WHERE status != 'Complete' ORDER BY createdAt DESC LIMIT 1")
    suspend fun findCurrent(): SessionEntity?

    @Query("SELECT * FROM sessions ORDER BY createdAt DESC LIMIT 1")
    fun observeLatest(): Flow<SessionEntity?>

    @Query("DELETE FROM sessions WHERE createdAt < :olderThanMillis")
    suspend fun deleteOlderThan(olderThanMillis: Long): Int
}
