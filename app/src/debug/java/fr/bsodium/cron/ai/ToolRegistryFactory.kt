package fr.bsodium.cron.ai

import android.content.Context
import fr.bsodium.cron.debug.FakeToolRegistry
import fr.bsodium.cron.debug.MockApiPrefs

/** DEBUG variant — returns [FakeToolRegistry] when mock mode is enabled and active; null otherwise. */
object ToolRegistryFactory {
    fun shouldUseMock(context: Context): Boolean {
        val prefs = MockApiPrefs(context)
        return prefs.isEnabled && prefs.isMockActive
    }

    fun mockOrNull(useMock: Boolean): ToolRegistry? =
        if (useMock) FakeToolRegistry.build() else null
}
