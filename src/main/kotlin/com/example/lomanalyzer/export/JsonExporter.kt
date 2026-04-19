package com.example.lomanalyzer.export

import com.example.lomanalyzer.observability.AppEvent
import com.example.lomanalyzer.observability.Logger
import com.example.lomanalyzer.security.PiiHasher
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.SecureRandom

@Serializable
data class JsonSessionExport(
    val schemaVersion: String = "1.0",
    val sessionId: Int,
    val sessionName: String,
    val privacyMode: String,
    val lomScores: List<JsonLomScore>,
    val anomalies: List<JsonAnomaly>,
    val riskSignals: List<JsonRiskSignal>,
)

@Serializable
data class JsonLomScore(
    val authorHash: String,
    val iBaseHist: Float?,
    val iEventHist: Float?,
    val roleCombined: String?,
    val confidence: Float?,
)

@Serializable
data class JsonAnomaly(
    val type: String,
    val dayDate: String,
    val severity: Float,
)

@Serializable
data class JsonRiskSignal(
    val riskScore: Float,
    val category: String?,
    val recommendation: String?,
)

/**
 * Full JSON export per v6 §26. Privacy-first: hashed PII by default.
 */
class JsonExporter(private val logger: Logger) {
    private val json = Json { prettyPrint = true }

    fun export(
        sessionId: Int,
        sessionName: String,
        lomScores: List<JsonLomScore>,
        anomalies: List<JsonAnomaly>,
        riskSignals: List<JsonRiskSignal>,
        outputFile: File,
        rawMode: Boolean = false,
    ) {
        val privacyMode = if (rawMode) "RAW" else "HASHED"

        val export = JsonSessionExport(
            sessionId = sessionId,
            sessionName = if (rawMode) sessionName else "Session #$sessionId",
            privacyMode = privacyMode,
            lomScores = lomScores,
            anomalies = anomalies,
            riskSignals = riskSignals,
        )

        outputFile.writeText(json.encodeToString(export))
        logger.event(AppEvent.EXPORT_CSV, mapOf(
            "format" to "JSON",
            "mode" to privacyMode,
            "file" to outputFile.name,
        ))
    }

    fun hashAuthorId(vkId: Int): String {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        return PiiHasher.hash(vkId.toString(), salt)
    }
}
