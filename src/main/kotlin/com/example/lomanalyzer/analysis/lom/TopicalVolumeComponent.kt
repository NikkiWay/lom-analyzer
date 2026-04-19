package com.example.lomanalyzer.analysis.lom

import kotlin.math.ln

/**
 * k_window normalization: k_window_a = baseline_days_actual_a / 60.
 * Seed authors: k_window = 1.0 (60-day baseline).
 * Discovery authors: k_window = 0.5 (30-day baseline).
 * V_raw_a = ln(1 + N_topic_eff_a / k_window_a).
 */
object TopicalVolumeComponent {
    private const val REFERENCE_BASELINE_DAYS = 60.0

    fun computeKWindow(baselineDaysActual: Int): Double =
        baselineDaysActual / REFERENCE_BASELINE_DAYS

    fun computeRaw(topicEffCount: Int, kWindow: Double): Double {
        val effectiveK = if (kWindow > 0) kWindow else 1.0
        return ln(1.0 + topicEffCount / effectiveK)
    }
}
