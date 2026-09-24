package fr.bsodium.cron.session.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        SessionEntity::class,
        SessionEventEntity::class,
        AiMessageEntity::class,
        MemoryEntity::class,
        ObservationEntity::class,
    ],
    version = 5,
    exportSchema = true,
)
abstract class CronDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun eventDao(): EventDao
    abstract fun aiMessageDao(): AiMessageDao
    abstract fun memoryDao(): MemoryDao
    abstract fun observationDao(): ObservationDao

    companion object {
        @Volatile private var instance: CronDatabase? = null

        fun get(context: Context): CronDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                CronDatabase::class.java,
                "cron.db",
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5).build().also { instance = it }
        }
    }
}
