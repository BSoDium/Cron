package fr.bsodium.cron.ui.screens.home

import android.accounts.AccountManager
import android.content.Context
import java.time.LocalTime

/**
 * Resolves the user's display name for the home greeting. We pull the primary
 * Google account name via [AccountManager]; on modern Android, the owning app
 * can read its own primary account's name without holding GET_ACCOUNTS.
 *
 * Returns null if no Google account is available, in which case the UI should
 * render the greeting without a name (e.g. "Good morning").
 */
fun resolveGreetingName(context: Context): String? {
    val accounts = runCatching {
        AccountManager.get(context).getAccountsByType("com.google")
    }.getOrNull() ?: return null
    val raw = accounts.firstOrNull()?.name?.takeIf { it.isNotBlank() } ?: return null
    val local = raw.substringBefore('@')
    val firstWord = local.substringBefore('.')
    return firstWord
        .trim()
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        .takeIf { it.isNotBlank() }
}

/**
 * Returns "Good morning" / "Good afternoon" / "Good evening" based on the
 * supplied local time. Defaults to night-friendly "Good evening" between
 * 18:00 and 04:00.
 */
fun greetingPrefix(now: LocalTime = LocalTime.now()): String = when (now.hour) {
    in 4..11 -> "Good morning"
    in 12..17 -> "Good afternoon"
    else -> "Good evening"
}
