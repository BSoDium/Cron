package fr.bsodium.cron.ui.screens.home

import fr.bsodium.cron.session.model.ActionType
import fr.bsodium.cron.session.model.SessionStatus
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/** The home screen's view of the current session: the resolved instruction plus the morning it targets. */
data class SessionDisplayState(
    val status: SessionStatus,
    val action: ActionType,
    val alarmTime: LocalTime?,
    val reason: String,
    val sessionDate: LocalDate,
    val snoozeCount: Int,
    val hardLatest: LocalTime? = null,
)
