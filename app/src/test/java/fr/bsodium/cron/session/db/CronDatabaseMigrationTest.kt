package fr.bsodium.cron.session.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CronDatabaseMigrationTest {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        CronDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrate1To2_addsMemoryEntriesTable_andAcceptsARow() {
        helper.createDatabase(TEST_DB, 1).close()

        val db = helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2)
        db.execSQL(
            "INSERT INTO memory_entries (text, category, createdAt, updatedAt) VALUES ('test', NULL, 0, 0)"
        )
        db.query("SELECT COUNT(*) FROM memory_entries").use { cursor ->
            cursor.moveToFirst()
            assertEquals(1, cursor.getInt(0))
        }
        db.close()
    }

    @Test
    fun migrate2To3_addsPendingColumn_defaultingToFalseForExistingRows() {
        helper.createDatabase(TEST_DB, 2).apply {
            execSQL(
                "INSERT INTO memory_entries (text, category, createdAt, updatedAt) VALUES ('pre-existing', NULL, 0, 0)"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 3, true, MIGRATION_2_3)
        db.execSQL(
            "INSERT INTO memory_entries (text, category, createdAt, updatedAt, pending) VALUES ('placeholder', NULL, 1, 1, 1)"
        )
        db.query("SELECT text, pending FROM memory_entries ORDER BY createdAt ASC").use { cursor ->
            cursor.moveToFirst()
            assertEquals("pre-existing", cursor.getString(0))
            assertEquals(0, cursor.getInt(1))
            cursor.moveToNext()
            assertEquals("placeholder", cursor.getString(0))
            assertEquals(1, cursor.getInt(1))
        }
        db.close()
    }

    @Test
    fun migrate3To4_addsProcessingMetadataColumns() {
        helper.createDatabase(TEST_DB, 3).close()

        val db = helper.runMigrationsAndValidate(TEST_DB, 4, true, MIGRATION_3_4)
        db.execSQL(
            "INSERT INTO memory_entries (text, category, createdAt, updatedAt, pending, instruction, failureReason) " +
                "VALUES ('', NULL, 0, 0, 0, 'remember tea', 'no_memory_added')",
        )
        db.query("SELECT instruction, failureReason FROM memory_entries").use { cursor ->
            cursor.moveToFirst()
            assertEquals("remember tea", cursor.getString(0))
            assertEquals("no_memory_added", cursor.getString(1))
        }
        db.close()
    }

    private companion object {
        const val TEST_DB = "migration-test"
    }
}
