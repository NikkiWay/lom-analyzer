package com.example.lomanalyzer.analysis.lom

import com.example.lomanalyzer.config.ReferenceBase
import kotlin.math.exp
import kotlin.math.ln

// TODO(Prompt-20): Implement MILD_RECOMPUTED branch with E-quantile recomputation
//   for |gamma_sess - 0.45| in (0.1, 0.2].

/**
 * Reference calibration — OK branch and AUDIENCE_ONLY_REFERENCE for MVP.
 *
 * OK: I_base_abs = 0.55 * sigmoid(z_A_ref) + 0.45 * sigmoid(z_E_ref)
 *   z_A = (ln(1+F) - q50_ln_F_ref) / (iqr_ln_F_ref + eps)
 *   z_E = (E_raw_sess - q50_E_ref) / (iqr_E_ref + eps)
 *
 * AUDIENCE_ONLY_REFERENCE: I_base_abs = sigmoid(z_A_ref)
 */
class ReferenceCalibrator(
    private val epsilon: Double = 1e-9,
) {
    companion object {
        private const val A_WEIGHT = 0.55
        private const val E_WEIGHT = 0.45
    }

    fun calibrate(
        followers: Int,
        eRawSession: Double,
        gammaDivergenceFlag: String,
        ref: ReferenceBase,
    ): Double {
        val lnF = ln(1.0 + followers)
        val lnFStats = ref.rawQuantileStatistics.lnF
        val zA = (lnF - lnFStats.q50) / (lnFStats.iqr + epsilon)
        val aNormRef = sigmoid(zA)

        return when (gammaDivergenceFlag) {
            "OK" -> {
                val eStats = ref.computedStatsAtGammaRef.eRawAtGamma
                val zE = (eRawSession - eStats.q50) / (eStats.iqr + epsilon)
                val eNormRef = sigmoid(zE)
                A_WEIGHT * aNormRef + E_WEIGHT * eNormRef
            }
            "AUDIENCE_ONLY_REFERENCE" -> aNormRef
            else -> aNormRef // REFERENCE_UNAVAILABLE etc. fallback
        }
    }

    fun getThreshold(ref: ReferenceBase): Double =
        ref.iBaseThresholds.tauBaseP75

    fun getFp75(ref: ReferenceBase): Int =
        ref.iBaseThresholds.fP75

    private fun sigmoid(z: Double): Double = 1.0 / (1.0 + exp(-z))
}
