package com.example.lomanalyzer.analysis.lom

import com.example.lomanalyzer.config.ReferenceBase
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln

/**
 * Full three-branch reference calibration per v6 §17.5.
 *
 * OK: direct reference quantiles for both A and E.
 * MILD_RECOMPUTED: recompute E-quantiles under session γ using raw ln_F/ln_r_bar quantiles.
 * AUDIENCE_ONLY_REFERENCE: I_base_abs = sigmoid(z_A) only.
 */
data class CalibrationResult(
    val iBaseAbs: Double,
    val flags: MutableSet<String> = mutableSetOf(),
)

class ReferenceCalibrator(
    private val epsilon: Double = 1e-9,
) {
    companion object {
        private const val A_WEIGHT = 0.55
        private const val E_WEIGHT = 0.45
        private const val HIGH_CORR_THRESHOLD = 0.6
    }

    fun calibrate(
        followers: Int,
        eRawSession: Double,
        gammaDivergenceFlag: String,
        sessionGamma: Double,
        ref: ReferenceBase,
    ): CalibrationResult {
        val lnF = ln(1.0 + followers)
        val lnFStats = ref.rawQuantileStatistics.lnF
        val zA = (lnF - lnFStats.q50) / (lnFStats.iqr + epsilon)
        val aNormRef = sigmoid(zA)
        val flags = mutableSetOf<String>()

        val iBaseAbs = when (gammaDivergenceFlag) {
            "OK" -> calibrateOk(eRawSession, ref, aNormRef)

            "MILD_RECOMPUTED" -> calibrateMild(
                eRawSession, sessionGamma, ref, aNormRef, flags,
            )

            else -> aNormRef // AUDIENCE_ONLY_REFERENCE or REFERENCE_UNAVAILABLE
        }

        return CalibrationResult(iBaseAbs, flags)
    }

    /**
     * Simplified overload for backward compatibility (OK + AUDIENCE_ONLY only).
     */
    fun calibrate(
        followers: Int,
        eRawSession: Double,
        gammaDivergenceFlag: String,
        ref: ReferenceBase,
    ): Double = calibrate(followers, eRawSession, gammaDivergenceFlag, 0.45, ref).iBaseAbs

    fun getThreshold(ref: ReferenceBase): Double = ref.iBaseThresholds.tauBaseP75
    fun getFp75(ref: ReferenceBase): Int = ref.iBaseThresholds.fP75

    private fun calibrateOk(
        eRawSession: Double,
        ref: ReferenceBase,
        aNormRef: Double,
    ): Double {
        val eStats = ref.computedStatsAtGammaRef.eRawAtGamma
        val zE = (eRawSession - eStats.q50) / (eStats.iqr + epsilon)
        val eNormRef = sigmoid(zE)
        return A_WEIGHT * aNormRef + E_WEIGHT * eNormRef
    }

    /**
     * MILD_RECOMPUTED per v6 §17.5.2:
     * q_p(E|γ_sess) ≈ q_p(ln_r_bar) - γ_sess * q_p(ln_F).
     * Derive IQR from recomputed q25, q75.
     * τ^ref_base approximated as 0.78 → flag REF_THRESHOLD_APPROXIMATED.
     * Correlation safeguard: flag HIGH_CORRELATION if corr(ln_F, ln_r_bar) > 0.6.
     */
    private fun calibrateMild(
        eRawSession: Double,
        sessionGamma: Double,
        ref: ReferenceBase,
        aNormRef: Double,
        flags: MutableSet<String>,
    ): Double {
        val lnFStats = ref.rawQuantileStatistics.lnF
        val lnRBarStats = ref.rawQuantileStatistics.lnRBar

        // Recompute E-quantiles under session gamma
        val eQ25 = lnRBarStats.q25 - sessionGamma * lnFStats.q25
        val eQ50 = lnRBarStats.q50 - sessionGamma * lnFStats.q50
        val eQ75 = lnRBarStats.q75 - sessionGamma * lnFStats.q75
        val eIqr = eQ75 - eQ25

        val zE = (eRawSession - eQ50) / (eIqr + epsilon)
        val eNormRef = sigmoid(zE)

        flags.add("REF_THRESHOLD_APPROXIMATED")

        // Correlation safeguard: approximate from quantile spread ratio
        // If IQR(ln_r_bar) tracks IQR(ln_F) closely → high correlation
        val corrProxy = estimateCorrelationProxy(lnFStats.iqr, lnRBarStats.iqr)
        if (corrProxy > HIGH_CORR_THRESHOLD) {
            flags.add("MILD_RECOMPUTED_HIGH_CORRELATION")
        }

        return A_WEIGHT * aNormRef + E_WEIGHT * eNormRef
    }

    /**
     * Proxy for correlation between ln_F and ln_r_bar from quantile statistics.
     * Uses IQR ratio as a heuristic: similar IQR spreads suggest correlated variables.
     */
    private fun estimateCorrelationProxy(iqrF: Double, iqrR: Double): Double {
        if (iqrF <= 0 || iqrR <= 0) return 0.0
        val ratio = minOf(iqrF, iqrR) / maxOf(iqrF, iqrR)
        return ratio // Higher ratio → more similar spreads → likely higher correlation
    }

    private fun sigmoid(z: Double): Double = 1.0 / (1.0 + exp(-z))
}
