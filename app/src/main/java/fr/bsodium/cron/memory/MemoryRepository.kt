package fr.bsodium.cron.memory

import android.content.Context
import fr.bsodium.cron.session.db.CronDatabase
import fr.bsodium.cron.session.db.MemoryEntity
import fr.bsodium.cron.session.db.toEntity
import fr.bsodium.cron.session.db.toModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock

/** Single source of truth for durable assistant memory entries. */
class MemoryRepository(private val context: Context) {

    private val db get() = CronDatabase.get(context)

    fun observeAll(): Flow<List<MemoryEntry>> =
        db.memoryDao().observeAll().map { entities -> entities.map { it.toModel() } }

    suspend fun currentAll(): List<MemoryEntry> = db.memoryDao().findAll().map { it.toModel() }

    suspend fun add(text: String, category: String?): Long {
        val now = Clock.System.now()
        return db.memoryDao().insert(
            MemoryEntry(id = 0, text = text, category = category, createdAt = now, updatedAt = now).toEntity(),
        )
    }

    suspend fun update(id: Long, text: String?, category: String?): Boolean {
        val existing = db.memoryDao().findAll().find { it.id == id } ?: return false
        val updated: MemoryEntity = existing.copy(
            text = text ?: existing.text,
            category = category ?: existing.category,
            updatedAt = Clock.System.now().toEpochMilliseconds(),
        )
        return db.memoryDao().update(updated) > 0
    }

    suspend fun delete(id: Long): Boolean = db.memoryDao().deleteById(id) > 0
}
