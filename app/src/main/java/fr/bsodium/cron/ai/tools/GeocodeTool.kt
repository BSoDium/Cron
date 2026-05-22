package fr.bsodium.cron.ai.tools

import fr.bsodium.cron.ai.Tool
import fr.bsodium.cron.ai.ToolResult
import fr.bsodium.cron.ai.toolSchema
import fr.bsodium.cron.ai.wire.ToolDefinition
import fr.bsodium.cron.session.db.SessionJson
import fr.bsodium.cron.travel.GeocodingClient
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class GeocodeTool(private val client: GeocodingClient) : Tool {

    @Serializable
    private data class Output(val lat: Double, val lng: Double, val formatted: String)

    override val definition: ToolDefinition = ToolDefinition(
        name = NAME,
        description = "Convert a human-readable address to lat/lng coordinates. Call before estimate_commute when you only have a string location from a calendar event.",
        input_schema = toolSchema(
            "address" to JsonObject(mapOf(
                "type" to JsonPrimitive("string"),
                "description" to JsonPrimitive("Full address or place name to geocode"),
            )),
            required = listOf("address"),
        ),
    )

    override suspend fun execute(input: JsonElement): ToolResult {
        val address = input.jsonObject["address"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
            ?: return ToolResult("""{"error":"address is required"}""", isError = true)

        val result = client.geocode(address).getOrElse { e ->
            return ToolResult("""{"error":"geocoding failed: ${e.message?.take(300)}"}""", isError = true)
        }

        return ToolResult(SessionJson.encodeToString(Output(result.lat, result.lng, result.formattedAddress)))
    }

    companion object {
        const val NAME = "geocode_address"
    }
}
