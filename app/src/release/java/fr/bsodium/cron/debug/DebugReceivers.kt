package fr.bsodium.cron.debug

import android.content.Context

/** RELEASE variant — no debug receivers to register. */
object DebugReceivers {
    @Suppress("UNUSED_PARAMETER")
    fun register(context: Context) {}
}
