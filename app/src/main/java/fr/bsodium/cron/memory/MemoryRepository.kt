package fr.bsodium.cron.memory

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import fr.bsodium.cron.session.db.CronDatabase
import fr.bsodium.cron.session.db.MemoryEntity
import fr.bsodium.cron.session.db.toEntity
import fr.bsodium.cron.session.db.toModel
import fr.bsodium.cron.worker.MemoryTurnWorker
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

    fun triggerMutation(instruction: String) {
        val data = Data.Builder()
            .putString(MemoryTurnWorker.KEY_INSTRUCTION, instruction)
            .build()
        val request = OneTimeWorkRequestBuilder<MemoryTurnWorker>()
            .setInputData(data)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            MemoryTurnWorker.WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    fun observeMutationWork(): Flow<List<WorkInfo>> =
        WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(MemoryTurnWorker.WORK_NAME)

    fun cancelMutation() {
        WorkManager.getInstance(context).cancelUniqueWork(MemoryTurnWorker.WORK_NAME)
    }
}
