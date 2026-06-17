package fr.bsodium.cron.ai

import android.content.Context

/** RELEASE variant — mock tools are never available. */
object ToolRegistryFactory {
    @Suppress("UNUSED_PARAMETER")
    fun mockOrNull(context: Context): ToolRegistry? = null
}
