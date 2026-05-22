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
    ],
    version = 1,
    exportSchema = false,
)
abstract class CronDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun eventDao(): EventDao
    abstract fun aiMessageDao(): AiMessageDao

    companion object {
        @Volatile private var instance: CronDatabase? = null

        fun get(context: Context): CronDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                CronDatabase::class.java,
                "cron.db",
            ).build().also { instance = it }
        }
    }
}
