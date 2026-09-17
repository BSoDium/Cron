package fr.bsodium.cron.session.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoryDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entry: MemoryEntity): Long

    @Update
    suspend fun update(entry: MemoryEntity): Int

    @Query("DELETE FROM memory_entries WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("SELECT * FROM memory_entries ORDER BY createdAt ASC")
    suspend fun findAll(): List<MemoryEntity>

    @Query("SELECT * FROM memory_entries WHERE id = :id")
    suspend fun findById(id: Long): MemoryEntity?

    /** Excludes in-flight placeholders (see `MemoryRepository.addPending`) -- for callers that feed
     *  this listing into an AI prompt, which shouldn't see a not-yet-real, possibly blank-text row. */
    @Query("SELECT * FROM memory_entries WHERE pending = 0 ORDER BY createdAt ASC")
    suspend fun findAllNonPending(): List<MemoryEntity>

    @Query("SELECT * FROM memory_entries ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<MemoryEntity>>

    @Query("DELETE FROM memory_entries")
    suspend fun deleteAll(): Int
}
