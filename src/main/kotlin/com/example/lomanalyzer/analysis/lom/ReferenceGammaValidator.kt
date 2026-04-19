package com.example.lomanalyzer.analysis.lom

import kotlin.math.abs

// TODO(Prompt-20): Implement MILD_RECOMPUTED branch for |gamma - 0.45| in (0.1, 0.2].

/**
 * MVP: OK-branch only.
 * OK if |gamma_sess - 0.45| <= 0.1, else AUDIENCE_ONLY_REFERENCE.
 */
object ReferenceGammaValidator {
    private const val GAMMA_REF = 0.45
    private const val OK_TOLERANCE = 0.1

    fun validate(sessionGamma: Double): String =
        if (abs(sessionGamma - GAMMA_REF) <= OK_TOLERANCE) "OK"
        else "AUDIENCE_ONLY_REFERENCE"
}
