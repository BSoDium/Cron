package fr.bsodium.cron.ai.tools

import fr.bsodium.cron.ai.Tool
import fr.bsodium.cron.ai.ToolResult
import fr.bsodium.cron.ai.toolErrorResult
import fr.bsodium.cron.ai.toolSchema
import fr.bsodium.cron.ai.wire.ToolDefinition
import fr.bsodium.cron.session.db.SessionJson
import fr.bsodium.cron.travel.GeocodingClient
import fr.bsodium.cron.travel.LatLng
import fr.bsodium.cron.travel.RoutesClient
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class EstimateCommuteTool(
    private val client: RoutesClient,
    private val geocoder: GeocodingClient,
    private val originBias: LatLng? = null,
) : Tool {

    @Serializable
    private data class Output(val duration_sec: Long, val distance_m: Int)

    override val definition: ToolDefinition = ToolDefinition(
        name = NAME,
        description = "Estimate commute duration from an origin (lat/lng) to a destination address. Default mode is TRANSIT. Use estimate_commute_multi_mode when the destination might be walkable.",
        input_schema = toolSchema(
            "origin_lat" to JsonObject(mapOf(
                "type" to JsonPrimitive("number"),
                "description" to JsonPrimitive("Origin latitude"),
            )),
            "origin_lng" to JsonObject(mapOf(
                "type" to JsonPrimitive("number"),
                "description" to JsonPrimitive("Origin longitude"),
            )),
            "destination" to JsonObject(mapOf(
                "type" to JsonPrimitive("string"),
                "description" to JsonPrimitive("Destination address"),
            )),
            "mode" to JsonObject(mapOf(
                "type" to JsonPrimitive("string"),
                "enum" to JsonArray(listOf("DRIVE", "TRANSIT", "WALK", "BICYCLE").map { JsonPrimitive(it) }),
                "description" to JsonPrimitive("Travel mode (default TRANSIT)"),
            )),
            "arrival_time_iso" to JsonObject(mapOf(
                "type" to JsonPrimitive("string"),
                "description" to JsonPrimitive("Desired arrival time as ISO-8601 instant (TRANSIT only, optional)"),
            )),
            required = listOf("origin_lat", "origin_lng", "destination"),
        ),
    )

    override suspend fun execute(input: JsonElement): ToolResult {
        val obj = input.jsonObject
        // Prefer the device's captured location as origin (overrides whatever the model passed — which
        // can be (0,0) when the prompt's location was unavailable); fall back to the model's values.
        val origin = originBias ?: run {
            val lat = obj["origin_lat"]?.jsonPrimitive?.content?.toDoubleOrNull()
                ?: return ToolResult("""{"error":"origin_lat required and must be a number"}""", isError = true)
            val lng = obj["origin_lng"]?.jsonPrimitive?.content?.toDoubleOrNull()
                ?: return ToolResult("""{"error":"origin_lng required and must be a number"}""", isError = true)
            LatLng(lat, lng)
        }
        val dest = obj["destination"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
            ?: return ToolResult("""{"error":"destination required"}""", isError = true)
        val mode = obj["mode"]?.jsonPrimitive?.content
            ?.let { runCatching { RoutesClient.TravelMode.valueOf(it) }.getOrNull() }
            ?: RoutesClient.TravelMode.TRANSIT
        val arrivalMs = obj["arrival_time_iso"]?.jsonPrimitive?.content
            ?.let { runCatching { Instant.parse(it).toEpochMilliseconds() }.getOrNull() }

        // Geocode the destination biased to the user's area so a same-named place elsewhere can't win.
        val target = geocoder.geocode(dest, originBias).getOrElse { e ->
            return toolErrorResult("geocoding failed: ${e.message?.take(300)}")
        }
        val result = client.estimate(origin.lat, origin.lng, target.lat, target.lng, mode, arrivalMs).getOrElse { e ->
            return toolErrorResult("commute estimate failed: ${e.message?.take(300)}")
        }

        return ToolResult(SessionJson.encodeToString(Output(result.durationSeconds, result.distanceMeters)))
    }

    companion object {
        const val NAME = "estimate_commute"
    }
}
