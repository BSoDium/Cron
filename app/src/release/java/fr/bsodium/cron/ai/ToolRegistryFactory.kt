package fr.bsodium.cron.ai

import android.content.Context

/** RELEASE variant — mock tools are never available. */
object ToolRegistryFactory {
    @Suppress("UNUSED_PARAMETER")
    fun shouldUseMock(context: Context): Boolean = false

    @Suppress("UNUSED_PARAMETER")
    fun mockOrNull(useMock: Boolean): ToolRegistry? = null
}
