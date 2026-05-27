package fr.bsodium.cron.ui.screens.home

import android.accounts.AccountManager
import android.content.Context
import java.time.LocalTime

/**
 * Best-effort prefill for the user's display name during onboarding.
 *
 * Since Android 8.0, [AccountManager.getAccountsByType] only returns accounts
 * that have granted visibility to the calling package. Google accounts created
 * via Settings → Accounts do not grant visibility to arbitrary third-party
 * apps, so this almost always returns an empty array on a real device,
 * regardless of how many Google accounts are signed in.
 *
 * Treat the return value strictly as a pre-fill hint for the onboarding name
 * field — the canonical name lives in `SettingsRepository.displayName`,
 * captured during onboarding.
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
