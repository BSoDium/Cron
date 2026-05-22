package fr.bsodium.cron.session.model

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ActionType {
    @SerialName("set_alarm") SetAlarm,
    @SerialName("cancel_alarm") CancelAlarm,
    @SerialName("send_brief") SendBrief,
    @SerialName("do_nothing") DoNothing,
    @SerialName("notify_warning") NotifyWarning,
}

@Serializable
data class Instruction(
    val action: ActionType,
    val alarmTime: LocalTime? = null,
    val wakeWindowStart: LocalTime? = null,
    val wakeWindowEnd: LocalTime? = null,
    val briefContent: String? = null,
    val reason: String,
    val issuedAt: Instant,
) {
    companion object {
        fun doNothing(reason: String, now: Instant): Instruction = Instruction(
            action = ActionType.DoNothing,
            reason = reason,
            issuedAt = now,
        )
    }
}
