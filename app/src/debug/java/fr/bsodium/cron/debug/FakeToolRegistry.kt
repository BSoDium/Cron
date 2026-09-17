package fr.bsodium.cron.debug

import fr.bsodium.cron.ai.Tool
import fr.bsodium.cron.ai.ToolRegistry
import fr.bsodium.cron.ai.ToolResult
import fr.bsodium.cron.ai.toolSchema
import fr.bsodium.cron.ai.wire.ToolDefinition
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * DEBUG-ONLY. Returns a [ToolRegistry] whose tools return canned responses
 * without calling any external API, so mock runs are fully local.
 */
object FakeToolRegistry {

    fun build(): ToolRegistry = ToolRegistry(
        listOf(
            stub(
                name = "read_calendar",
                description = "Read calendar events",
                response = """{"events":[{"title":"Team standup","start":"2025-12-17T08:45:00Z","end":"2025-12-17T09:30:00Z","location":"Office","all_day":false}]}""",
            ),
            stub(
                name = "estimate_commute",
                description = "Estimate commute duration",
                response = """{"duration_sec":1320,"distance_m":8500}""",
            ),
            stub(
                name = "estimate_commute_multi_mode",
                description = "Estimate commute duration across multiple modes",
                response = """{"TRANSIT":{"duration_sec":1320,"distance_m":8500},"DRIVE":{"duration_sec":960,"distance_m":9200}}""",
            ),
            stub(
                name = "geocode_address",
                description = "Geocode an address to lat/lng",
                response = """{"lat":48.8566,"lng":2.3522}""",
            ),
            setAlarmStub(),
            stub(
                name = "do_nothing",
                description = "Skip planning",
                response = """{"status":"ok"}""",
            ),
            stub(
                name = "cancel_alarm",
                description = "Cancel an existing alarm",
                response = """{"status":"ok"}""",
            ),
            stub(
                name = "send_brief",
                description = "Send a morning brief notification",
                response = """{"status":"ok"}""",
            ),
            stub(
                name = "notify_warning",
                description = "Send a warning notification",
                response = """{"status":"ok"}""",
            ),
        ),
    )

    private fun stub(name: String, description: String, response: String) = object : Tool {
        override val definition = ToolDefinition(
            name = name,
            description = description,
            input_schema = toolSchema(),
        )

        override suspend fun execute(input: JsonElement) = ToolResult(response)
    }

    /** Echoes the requested alarm time so mocked timeline headlines resolve correctly. */
    private fun setAlarmStub() = object : Tool {
        override val definition = ToolDefinition(
            name = "set_alarm",
            description = "Set an alarm",
            input_schema = toolSchema(),
        )

        override suspend fun execute(input: JsonElement): ToolResult {
            val timeIso = input.jsonObject["time_iso"]?.jsonPrimitive?.content
            return ToolResult("""{"status":"ok","alarm_time":"$timeIso"}""")
        }
    }
}
