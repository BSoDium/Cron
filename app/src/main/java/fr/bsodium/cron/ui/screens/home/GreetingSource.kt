package fr.bsodium.cron.ui.screens.home

import java.time.LocalTime

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
