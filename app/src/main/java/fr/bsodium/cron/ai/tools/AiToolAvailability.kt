package fr.bsodium.cron.ai.tools

import fr.bsodium.cron.BuildConfig

/** One AI tool's registration status — read-only diagnostic info surfaced in Settings → Developer
 *  to explain why the model can't call a given tool. Mirrors `AiTurnWorker.buildToolRegistry`'s
 *  actual gating rather than re-deriving it independently; keep both in sync if a new conditional
 *  tool is added there. */
internal data class AiToolStatus(val name: String, val available: Boolean, val reason: String? = null)

/** The only conditional gate today is the Google Routes API key, which suppresses the three
 *  location tools as a group; every other tool is always registered. */
internal fun aiToolAvailability(): List<AiToolStatus> {
    val routesConfigured = BuildConfig.GOOGLE_ROUTES_API_KEY.isNotBlank()
    val routesReason = "GOOGLE_ROUTES_API_KEY is blank in local.properties".takeIf { !routesConfigured }
    return listOf(
        AiToolStatus(ReadCalendarTool.NAME, available = true),
        AiToolStatus(GeocodeTool.NAME, available = routesConfigured, reason = routesReason),
        AiToolStatus(EstimateCommuteTool.NAME, available = routesConfigured, reason = routesReason),
        AiToolStatus(EstimateCommuteMultiModeTool.NAME, available = routesConfigured, reason = routesReason),
        AiToolStatus(SetAlarmTool.NAME, available = true),
        AiToolStatus(DoNothingTool.NAME, available = true),
        AiToolStatus(CancelAlarmTool.NAME, available = true),
        AiToolStatus(SendBriefTool.NAME, available = true),
        AiToolStatus(NotifyWarningTool.NAME, available = true),
    )
}
