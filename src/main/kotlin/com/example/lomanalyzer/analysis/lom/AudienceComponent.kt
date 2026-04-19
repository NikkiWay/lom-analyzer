package com.example.lomanalyzer.analysis.lom

import kotlin.math.ln

/**
 * A_raw = ln(1 + F) per author from baseline window.
 */
object AudienceComponent {
    fun computeRaw(followers: Int): Double =
        ln(1.0 + followers)
}
