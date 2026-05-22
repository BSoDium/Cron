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
 * Terminal tool: shows a morning brief notification and persists it as
 * the session's current instruction.
 *
 * Use when the user is confirmed awake and a day summary is more useful
 * than firing an alarm (e.g., OutOfBedConfirmed with 10+ minutes elapsed
 * and the wake window is still open).
 */
class SendBriefTool(
    private val context: Context,
    private val repository: SessionRepository,
    private val sessionId: String,
) : Tool {

    override val definition: ToolDefinition = ToolDefinition(
        name = NAME,
        description = "Show a morning brief notification to the user and cancel the pending alarm. Use when the user is already awake and a summary message is more appropriate than an alarm.",
        input_schema = toolSchema(
            "content" to JsonObject(mapOf(
                "type" to JsonPrimitive("string"),
                "description" to JsonPrimitive("Short morning summary shown as the notification body (1-2 sentences)"),
            )),
            "reason" to JsonObject(mapOf(
                "type" to JsonPrimitive("string"),
                "description" to JsonPrimitive("One-sentence internal explanation of why a brief is appropriate"),
            )),
            required = listOf("content", "reason"),
        ),
    )

    override suspend fun execute(input: JsonElement): ToolResult {
        val obj = input.jsonObject
        val content = obj["content"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
            ?: return ToolResult("""{"error":"content is required"}""", isError = true)
        val reason = obj["reason"]?.jsonPrimitive?.content ?: ""

        val notification = NotificationCompat.Builder(context, AlarmReceiver.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle("Good morning")
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, notification)

        repository.updateInstruction(
            sessionId,
            Instruction(
                action = ActionType.SendBrief,
                briefContent = content,
                reason = reason,
                issuedAt = Clock.System.now(),
            ),
        )
        return ToolResult("""{"sent":true}""")
    }

    companion object {
        const val NAME = "send_brief"
        const val NOTIFICATION_ID = 9002
    }
}
