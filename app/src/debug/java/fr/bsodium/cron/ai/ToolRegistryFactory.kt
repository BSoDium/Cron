package fr.bsodium.cron.ai

import android.content.Context
import fr.bsodium.cron.debug.FakeToolRegistry
import fr.bsodium.cron.debug.MockApiPrefs

/** DEBUG variant — returns [FakeToolRegistry] when mock mode is on; null otherwise (caller builds real). */
object ToolRegistryFactory {
    fun mockOrNull(context: Context): ToolRegistry? =
        if (MockApiPrefs(context).isEnabled) FakeToolRegistry.build() else null
}
