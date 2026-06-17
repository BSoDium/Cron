package fr.bsodium.cron.ai

import android.content.Context
import fr.bsodium.cron.debug.FakeToolRegistry
import fr.bsodium.cron.debug.MockApiPrefs

/** DEBUG variant — returns [FakeToolRegistry] when mock mode is enabled and active; null otherwise. */
object ToolRegistryFactory {
    fun mockOrNull(context: Context): ToolRegistry? {
        val prefs = MockApiPrefs(context)
        return if (prefs.isEnabled && prefs.isMockActive) FakeToolRegistry.build() else null
    }
}
