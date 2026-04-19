package com.example.lomanalyzer.config

import com.example.lomanalyzer.observability.AppEvent
import com.example.lomanalyzer.observability.Logger
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.MessageDigest

@Serializable
data class QuantileStats(
    val q10: Double = 0.0,
    val q25: Double = 0.0,
    val q50: Double = 0.0,
    val q75: Double = 0.0,
    val q90: Double = 0.0,
    val iqr: Double = 0.0,
)

@Serializable
data class IBasePercentiles(val p50: Double = 0.0, val p75: Double = 0.0, val p90: Double = 0.0)

@Serializable
data class RawQuantileStatistics(
    @SerialName("ln_F") val lnF: QuantileStats = QuantileStats(),
    @SerialName("ln_r_bar") val lnRBar: QuantileStats = QuantileStats(),
)

@Serializable
data class ComputedStatsAtGamma(
    @SerialName("E_raw_at_gamma_0_45") val eRawAtGamma: QuantileStats = QuantileStats(),
    @SerialName("I_base_at_gamma_0_45") val iBaseAtGamma: IBasePercentiles = IBasePercentiles(),
)

@Serializable
data class IBaseThresholds(
    @SerialName("tau_base_p75_at_gamma_ref") val tauBaseP75: Double = 0.78,
    @SerialName("F_p75") val fP75: Int = 13000,
)

@Serializable
data class ReferenceBase(
    val version: String = "",
    @SerialName("collected_at") val collectedAt: String = "",
    @SerialName("sample_size") val sampleSize: Int = 0,
    @SerialName("gamma_used_in_collection") val gammaUsedInCollection: Double = 0.45,
    @SerialName("raw_quantile_statistics") val rawQuantileStatistics: RawQuantileStatistics = RawQuantileStatistics(),
    @SerialName("computed_statistics_at_gamma_ref")
    val computedStatsAtGammaRef: ComputedStatsAtGamma = ComputedStatsAtGamma(),
    @SerialName("I_base_thresholds") val iBaseThresholds: IBaseThresholds = IBaseThresholds(),
)

class ResourceLoader(private val logger: Logger) {
    private val json = Json { ignoreUnknownKeys = true }

    var referenceBase: ReferenceBase? = null
        private set

    @Suppress("ReturnCount")
    fun loadReferenceBase(expectedSha256: String? = null): ReferenceBase? {
        val content = loadResource("/resources/reference_base.json") ?: return null
        if (expectedSha256 != null && !verifySha256(content, expectedSha256)) {
            logger.event(AppEvent.REFERENCE_BASE_MISMATCH, mapOf(
                "expected" to expectedSha256,
                "actual" to sha256(content),
            ))
            return null
        }
        referenceBase = json.decodeFromString<ReferenceBase>(content)
        logger.event(AppEvent.REFERENCE_BASE_LOADED, mapOf(
            "version" to (referenceBase?.version ?: "unknown"),
        ))
        return referenceBase
    }

    fun loadHolidays(): String? = loadResource("/resources/holidays.json")
    fun loadSentilex(): String? = loadResource("/resources/sentilex_base.json")
    fun loadTestCorpus(): String? = loadResource("/resources/test_corpus.json")

    fun computeSha256(resourcePath: String): String? {
        val content = loadResource(resourcePath) ?: return null
        return sha256(content)
    }

    private fun loadResource(path: String): String? =
        ResourceLoader::class.java.getResourceAsStream(path)?.bufferedReader()?.readText()

    private fun verifySha256(content: String, expected: String): Boolean =
        sha256(content).equals(expected, ignoreCase = true)

    private fun sha256(content: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(content.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
