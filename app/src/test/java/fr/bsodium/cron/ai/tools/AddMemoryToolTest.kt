package fr.bsodium.cron.ai.tools

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import fr.bsodium.cron.memory.MemoryRepository
import fr.bsodium.cron.session.db.CronDatabase
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Covers [AddMemoryTool]'s placeholder-reuse contract: the model can call add_memory zero, one, or
 *  several times per turn, but there's only one placeholder row to reuse -- see MemoryTurnWorker's
 *  KDoc for the full flow this feeds into. */
@RunWith(RobolectricTestRunner::class)
class AddMemoryToolTest {

    private lateinit var repository: MemoryRepository

    @Before
    fun setUp() {
        val app: Application = ApplicationProvider.getApplicationContext()
        runTest { CronDatabase.get(app).memoryDao().deleteAll() }
        repository = MemoryRepository(app)
    }

    private fun addMemoryInput(text: String, category: String? = null) = buildJsonObject {
        put("text", text)
        category?.let { put("category", it) }
    }

    @Test
    fun firstCall_finalizesThePlaceholderInPlace_insteadOfInsertingASecondRow() = runTest {
        val placeholderId = repository.addPending()
        val tool = AddMemoryTool(repository, placeholderId)

        val result = tool.execute(addMemoryInput("Commutes by bike", "transport"))

        assertFalse(result.isError)
        assertTrue(tool.placeholderConsumed)
        val all = repository.currentAll()
        assertEquals(1, all.size)
        assertEquals(placeholderId, all.single().id)
        assertEquals("Commutes by bike", all.single().text)
        assertFalse(all.single().pending)
    }

    @Test
    fun secondCallInSameTurn_insertsANewRow_sinceThePlaceholderIsAlreadyConsumed() = runTest {
        val placeholderId = repository.addPending()
        val tool = AddMemoryTool(repository, placeholderId)

        tool.execute(addMemoryInput("Commutes by bike"))
        tool.execute(addMemoryInput("Prefers tea over coffee"))

        val all = repository.currentAll()
        assertEquals(2, all.size)
        assertTrue(all.none { it.pending })
    }

    @Test
    fun noPlaceholder_insertsNormally() = runTest {
        val tool = AddMemoryTool(repository, placeholderId = null)

        tool.execute(addMemoryInput("Commutes by bike"))

        assertFalse(tool.placeholderConsumed)
        assertEquals(1, repository.currentAll().size)
    }

    @Test
    fun placeholderDeletedOutFromUnderTheTurn_fallsBackToANormalInsert() = runTest {
        val placeholderId = repository.addPending()
        repository.delete(placeholderId)
        val tool = AddMemoryTool(repository, placeholderId)

        val result = tool.execute(addMemoryInput("Commutes by bike"))

        assertFalse(result.isError)
        assertFalse(tool.placeholderConsumed)
        assertEquals(1, repository.currentAll().size)
    }
}
