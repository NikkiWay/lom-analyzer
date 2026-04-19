package com.example.lomanalyzer.analysis.lom

import kotlin.math.abs

/**
 * Three-branch γ-divergence policy per v6 §17.5.1.
 *
 * | Condition              | Strategy                    | Flag                      |
 * |------------------------|-----------------------------|---------------------------|
 * | |Δγ| <= 0.1            | Direct reference quantiles  | OK                        |
 * | 0.1 < |Δγ| <= 0.2     | Recompute E-quantiles       | MILD_RECOMPUTED           |
 * | |Δγ| > 0.2             | Audience-only formula       | AUDIENCE_ONLY_REFERENCE   |
 */
object ReferenceGammaValidator {
    private const val GAMMA_REF = 0.45
    private const val OK_TOLERANCE = 0.1
    private const val MILD_TOLERANCE = 0.2

    fun validate(sessionGamma: Double): String {
        val delta = abs(sessionGamma - GAMMA_REF)
        return when {
            delta <= OK_TOLERANCE -> "OK"
            delta <= MILD_TOLERANCE -> "MILD_RECOMPUTED"
            else -> "AUDIENCE_ONLY_REFERENCE"
        }
    }
}
