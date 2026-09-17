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
        // Reset the file-backed singleton so each test starts clean.
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

    @Test
    fun failedPendingRow_staysVisibleWithReason_andCanBeRetried() = runTest {
        val id = repository.addPending("Remember that I prefer tea")
        repository.markFailed(id, "no_memory_added")

        val failed = repository.observeAll().first().single()
        assertFalse(failed.pending)
        assertEquals("no_memory_added", failed.failureReason)
        assertEquals("Remember that I prefer tea", failed.instruction)
        assertTrue(repository.currentAll().isEmpty())

        assertEquals("Remember that I prefer tea", repository.retry(id))
        val retried = repository.observeAll().first().single()
        assertTrue(retried.pending)
        assertNull(retried.failureReason)
    }
}
