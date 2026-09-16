package fr.bsodium.cron.memory

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import fr.bsodium.cron.session.db.CronDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Covers the insert-then-finalize pending-row lifecycle (issue: a mutation turn's real entry used
 *  to land as a second row next to a separate fake placeholder). */
@RunWith(RobolectricTestRunner::class)
class MemoryRepositoryTest {

    private lateinit var repository: MemoryRepository

    @Before
    fun setUp() {
        val app: Application = ApplicationProvider.getApplicationContext()
        // The production CronDatabase singleton is file-backed and persists across tests in the JVM --
        // wipe it so each test starts from a clean slate (same idiom as TimelineRepositoryTest).
        runTest { CronDatabase.get(app).memoryDao().deleteAll() }
        repository = MemoryRepository(app)
    }

    @Test
    fun addPending_insertsABlankPendingRow_thatObserveAllIncludesButCurrentAllExcludes() = runTest {
        val id = repository.addPending()

        val observed = repository.observeAll().first()
        val all = repository.currentAll()

        assertTrue(observed.any { it.id == id && it.pending })
        assertTrue(all.none { it.id == id })
    }

    @Test
    fun finalizePending_updatesTheSameRowInPlace_ratherThanInsertingASecondOne() = runTest {
        val id = repository.addPending()

        val finalized = repository.finalizePending(id, text = "Commutes by bike", category = "transport")

        assertTrue(finalized)
        val all = repository.currentAll()
        assertEquals(1, all.size)
        assertEquals(id, all.single().id)
        assertEquals("Commutes by bike", all.single().text)
        assertEquals("transport", all.single().category)
        assertFalse(all.single().pending)
    }

    @Test
    fun finalizePending_returnsFalse_whenTheRowNoLongerExists() = runTest {
        val id = repository.addPending()
        repository.delete(id)

        val finalized = repository.finalizePending(id, text = "Commutes by bike", category = null)

        assertFalse(finalized)
    }

    @Test
    fun currentAll_excludesPendingRows_whileStillCountingRealOnes() = runTest {
        repository.add(text = "Real entry", category = null)
        repository.addPending()

        val all = repository.currentAll()

        assertEquals(1, all.size)
        assertEquals("Real entry", all.single().text)
        assertNull(all.singleOrNull { it.pending })
    }
}
