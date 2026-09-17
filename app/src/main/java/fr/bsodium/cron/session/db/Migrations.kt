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
