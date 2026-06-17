package fr.bsodium.cron.calendar

import kotlinx.serialization.Serializable

@Serializable
enum class RsvpStatus(val label: String, val codes: Set<Int>) {
    Accepted("Accepted", setOf(1)),
    NotResponded("Not responded", setOf(0, 3)),
    Tentative("Maybe", setOf(4)),
    Declined("Declined", setOf(2)),
    ;

    companion object {
        fun fromCode(code: Int): RsvpStatus? = entries.firstOrNull { code in it.codes }
    }
}

val DEFAULT_RSVP_STATUSES: Set<RsvpStatus> =
    setOf(RsvpStatus.Accepted, RsvpStatus.NotResponded, RsvpStatus.Tentative)
