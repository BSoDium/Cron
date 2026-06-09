package fr.bsodium.cron.session.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A transport mode the planner may use for commute estimates. The user can exclude modes in settings;
 * the planner still auto-picks the fastest among the allowed ones. [promptToken] is the exact value the
 * `estimate_commute` tool accepts, so the prompt can list the allowed modes verbatim.
 */
@Serializable
enum class CommuteMode(val label: String, val promptToken: String) {
    @SerialName("drive") Drive("Drive", "DRIVE"),
    @SerialName("transit") Transit("Public transit", "TRANSIT"),
    @SerialName("bike") Bike("Bike", "BICYCLE"),
    @SerialName("walk") Walk("Walk", "WALK"),
}
