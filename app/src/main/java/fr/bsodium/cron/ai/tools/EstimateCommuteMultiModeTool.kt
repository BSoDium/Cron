package fr.bsodium.cron.ai.tools

import fr.bsodium.cron.ai.Tool
import fr.bsodium.cron.ai.ToolResult
import fr.bsodium.cron.ai.toolSchema
import fr.bsodium.cron.ai.wire.ToolDefinition
import fr.bsodium.cron.session.db.SessionJson
import fr.bsodium.cron.travel.RoutesClient
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class EstimateCommuteMultiModeTool(private val client: RoutesClient) : Tool {

    @Serializable
    private data class ModeResult(val duration_sec: Long, val distance_m: Int)

    @Serializable
    private data class Output(
        val transit: ModeResult? = null,
        val walk: ModeResult? = null,
        val drive: ModeResult? = null,
        val bicycle: ModeResult? = null,
    )

    override val definition: ToolDefinition = ToolDefinition(
        name = NAME,
        description = "Estimate commute time for all travel modes in parallel. Use when the destination might be walkable (<1 km) or when you want to compare options and pick the best.",
        input_schema = toolSchema(
            "origin_lat" to JsonObject(mapOf("type" to JsonPrimitive("number"))),
            "origin_lng" to JsonObject(mapOf("type" to JsonPrimitive("number"))),
            "destination" to JsonObject(mapOf(
                "type" to JsonPrimitive("string"),
                "description" to JsonPrimitive("Destination address"),
            )),
            "arrival_time_iso" to JsonObject(mapOf(
                "type" to JsonPrimitive("string"),
                "description" to JsonPrimitive("Desired arrival time ISO-8601 for TRANSIT (optional)"),
            )),
            required = listOf("origin_lat", "origin_lng", "destination"),
        ),
    )

    override suspend fun execute(input: JsonElement): ToolResult = coroutineScope {
        val obj = input.jsonObject
        val lat = obj["origin_lat"]?.jsonPrimitive?.content?.toDoubleOrNull()
            ?: return@coroutineScope ToolResult("""{"error":"origin_lat required"}""", isError = true)
        val lng = obj["origin_lng"]?.jsonPrimitive?.content?.toDoubleOrNull()
            ?: return@coroutineScope ToolResult("""{"error":"origin_lng required"}""", isError = true)
        val dest = obj["destination"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
            ?: return@coroutineScope ToolResult("""{"error":"destination required"}""", isError = true)
        val arrivalMs = obj["arrival_time_iso"]?.jsonPrimitive?.content
            ?.let { runCatching { Instant.parse(it).toEpochMilliseconds() }.getOrNull() }

        val transitD = async { client.estimate(lat, lng, dest, RoutesClient.TravelMode.TRANSIT, arrivalMs) }
        val walkD = async { client.estimate(lat, lng, dest, RoutesClient.TravelMode.WALK) }
        val driveD = async { client.estimate(lat, lng, dest, RoutesClient.TravelMode.DRIVE) }
        val bicycleD = async { client.estimate(lat, lng, dest, RoutesClient.TravelMode.BICYCLE) }

        fun RoutesClient.RouteResult.toMode() = ModeResult(durationSeconds, distanceMeters)
        val output = Output(
            transit = transitD.await()?.toMode(),
            walk = walkD.await()?.toMode(),
            drive = driveD.await()?.toMode(),
            bicycle = bicycleD.await()?.toMode(),
        )
        ToolResult(SessionJson.encodeToString(output))
    }

    companion object {
        const val NAME = "estimate_commute_multi_mode"
    }
}
