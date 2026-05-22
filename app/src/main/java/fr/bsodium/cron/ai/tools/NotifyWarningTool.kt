package fr.bsodium.cron.ai.tools

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import fr.bsodium.cron.R
import fr.bsodium.cron.ai.Tool
import fr.bsodium.cron.ai.ToolResult
import fr.bsodium.cron.ai.toolSchema
import fr.bsodium.cron.ai.wire.ToolDefinition
import fr.bsodium.cron.receiver.AlarmReceiver
import fr.bsodium.cron.session.SessionRepository
import fr.bsodium.cron.session.model.ActionType
import fr.bsodium.cron.session.model.Instruction
import kotlinx.datetime.Clock
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Terminal tool: surfaces a problem or constraint to the user as a
 * high-priority notification.
 *
 * Use when something unexpected prevents normal alarm scheduling and the
 * user should be informed (e.g., permissions revoked, calendar unreadable,
 * conflicting events detected).
 */
class NotifyWarningTool(
    private val context: Context,
    private val repository: SessionRepository,
    private val sessionId: String,
) : Tool {

    override val definition: ToolDefinition = ToolDefinition(
        name = NAME,
        description = "Surface a problem or constraint to the user via a high-priority notification. Use when something prevents normal scheduling and the user needs to be informed.",
        input_schema = toolSchema(
            "message" to JsonObject(mapOf(
                "type" to JsonPrimitive("string"),
                "description" to JsonPrimitive("Short warning message shown to the user (1 sentence)"),
            )),
            "reason" to JsonObject(mapOf(
                "type" to JsonPrimitive("string"),
                "description" to JsonPrimitive("Internal explanation of what went wrong"),
            )),
            required = listOf("message", "reason"),
        ),
    )

    override suspend fun execute(input: JsonElement): ToolResult {
        val obj = input.jsonObject
        val message = obj["message"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
            ?: return ToolResult("""{"error":"message is required"}""", isError = true)
        val reason = obj["reason"]?.jsonPrimitive?.content ?: ""

        val notification = NotificationCompat.Builder(context, AlarmReceiver.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle("Cron — warning")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, notification)

        repository.updateInstruction(
            sessionId,
            Instruction(
                action = ActionType.NotifyWarning,
                reason = reason,
                issuedAt = Clock.System.now(),
            ),
        )
        return ToolResult("""{"notified":true}""")
    }

    companion object {
        const val NAME = "notify_warning"
        const val NOTIFICATION_ID = 9003
    }
}
