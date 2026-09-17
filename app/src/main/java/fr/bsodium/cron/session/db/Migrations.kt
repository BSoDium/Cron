package fr.bsodium.cron.session.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Adds the `memory_entries` table. Not FK'd to `sessions` -- memory is not session-scoped. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS memory_entries (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "text TEXT NOT NULL, " +
                "category TEXT, " +
                "createdAt INTEGER NOT NULL, " +
                "updatedAt INTEGER NOT NULL)"
        )
    }
}

/** Adds `pending`, marking a row inserted as an in-flight placeholder before the assistant's
 *  mutation turn finalizes it -- see [fr.bsodium.cron.memory.MemoryRepository.addPending]. */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE memory_entries ADD COLUMN pending INTEGER NOT NULL DEFAULT 0")
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE memory_entries ADD COLUMN instruction TEXT")
        db.execSQL("ALTER TABLE memory_entries ADD COLUMN failureReason TEXT")
    }
}
