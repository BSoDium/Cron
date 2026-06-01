package fr.bsodium.cron.ai.tools

import fr.bsodium.cron.ai.Tool
import fr.bsodium.cron.ai.ToolResult
import fr.bsodium.cron.ai.toolSchema
import fr.bsodium.cron.ai.wire.ToolDefinition
import fr.bsodium.cron.calendar.CalendarReader
import fr.bsodium.cron.session.db.SessionJson
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Exposes [CalendarReader] to the model.
 *
 * Input: { start_iso: ISO-8601 instant, end_iso: ISO-8601 instant }
 * Output: { timezone, events: [{ id, title, date_local, start_local, end_local, all_day, location }] }
 *
 * Times are always emitted as local wall-clock strings so the model reasons in the
 * user's timezone rather than UTC. Location is always present (empty string when absent)
 * to work around SessionJson's explicitNulls=false omitting null fields.
 */
class ReadCalendarTool(private val reader: CalendarReader) : Tool {

    @Serializable
    private data class EventOut(
        val id: Long,
        val title: String,
        val date_local: String,
        val start_local: String,
        val end_local: String,
        val start_utc_iso: String,
        val all_day: Boolean,
        val location: String,
        val timezone: String,
    )

    @Serializable
    private data class Output(val timezone: String, val events: List<EventOut>)

    override val definition: ToolDefinition = ToolDefinition(
        name = NAME,
        description = """
            Read the user's calendar events between two timestamps to find the next anchor the
            user must be ready for. Online/virtual events (location is a URL, a chat channel, or
            empty but timed) are real anchors but need no commute. All-day entries are not anchors:
            most are markers (birthday, OOO), but one whose title is a place ("Office", a city, an
            address) or Home/Remote indicates the day's working location — use it as the commute
            destination for a physical anchor that lacks its own location.
        """.trimIndent(),
        input_schema = toolSchema(
            "start_iso" to JsonObject(
                mapOf(
                    "type" to JsonPrimitive("string"),
                    "description" to JsonPrimitive("ISO-8601 start of range, e.g. 2026-05-22T00:00:00Z"),
                ),
            ),
            "end_iso" to JsonObject(
                mapOf(
                    "type" to JsonPrimitive("string"),
                    "description" to JsonPrimitive("ISO-8601 end of range. Should typically be 24-30 hours after start."),
                ),
            ),
            required = listOf("start_iso", "end_iso"),
        ),
    )

    override suspend fun execute(input: JsonElement): ToolResult {
        val startStr = input.jsonObject["start_iso"]?.jsonPrimitive?.contentOrNullSafe()
        val endStr = input.jsonObject["end_iso"]?.jsonPrimitive?.contentOrNullSafe()
        if (startStr.isNullOrBlank() || endStr.isNullOrBlank()) {
            return ToolResult(
                payload = """{"error":"start_iso and end_iso are required"}""",
                isError = true,
            )
        }
        val start = runCatching { Instant.parse(startStr) }.getOrNull()
        val end = runCatching { Instant.parse(endStr) }.getOrNull()
        if (start == null || end == null) {
            return ToolResult(
                payload = """{"error":"start_iso and end_iso must be ISO-8601 instants"}""",
                isError = true,
            )
        }
        if (end <= start) {
            return ToolResult(
                payload = """{"error":"end_iso must be after start_iso"}""",
                isError = true,
            )
        }

        val tz = TimeZone.currentSystemDefault()
        val events = reader.readEvents(start, end)
        val eventsOut = events.map { e ->
            val startLdt = e.start.toLocalDateTime(tz)
            val endLdt = e.end.toLocalDateTime(tz)
            EventOut(
                id = e.id,
                title = e.title,
                date_local = startLdt.date.toString(),
                start_local = startLdt.time.toString().substring(0, 5),
                end_local = endLdt.time.toString().substring(0, 5),
                start_utc_iso = e.start.toString(),
                all_day = e.allDay,
                location = e.location ?: "",
                timezone = tz.id,
            )
        }
        return ToolResult(SessionJson.encodeToString(Output(timezone = tz.id, events = eventsOut)))
    }

    private fun kotlinx.serialization.json.JsonPrimitive.contentOrNullSafe(): String? =
        if (isString) content else content.takeIf { it.isNotBlank() }

    companion object {
        const val NAME = "read_calendar"
    }
}
