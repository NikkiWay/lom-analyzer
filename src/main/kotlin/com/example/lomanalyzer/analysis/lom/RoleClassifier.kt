package com.example.lomanalyzer.analysis.lom

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Session quadrant assignment per v6 §18.1.
 * Thresholds: tau_base = median(I_base_hist), tau_event = median(I_event_hist).
 */
enum class SessionRole {
    AUTHORITATIVE_LOM,
    SLEEPING_GIANT,
    TOPIC_DRIVER,
    BACKGROUND,
}

object RoleClassifier {
    fun classifySession(
        iBaseHist: Double,
        iEventHist: Double,
        tauBase: Double,
        tauEvent: Double,
    ): SessionRole = when {
        iBaseHist >= tauBase && iEventHist >= tauEvent -> SessionRole.AUTHORITATIVE_LOM
        iBaseHist >= tauBase && iEventHist < tauEvent -> SessionRole.SLEEPING_GIANT
        iBaseHist < tauBase && iEventHist >= tauEvent -> SessionRole.TOPIC_DRIVER
        else -> SessionRole.BACKGROUND
    }

    /**
     * Compute confidence per v6 §16.6.
     * d_base = |I_base - tau_base| / q_base
     * d_event = |I_event - tau_event| / q_event
     * confidence_raw = min(d_base, d_event)
     * confidence = clamp(confidence_raw * sqrt(n_eff) / (sqrt(n_eff) + 10), 0, 1)
     *
     * For BASELINE_UNKNOWN: confidence = 0.
     */
    @Suppress("LongParameterList", "ReturnCount")
    fun computeConfidence(
        iBaseHist: Double,
        iEventHist: Double,
        tauBase: Double,
        tauEvent: Double,
        qBase: Double,
        qEvent: Double,
        nEff: Int,
        isBaselineUnknown: Boolean = false,
    ): Double {
        if (isBaselineUnknown) return 0.0
        if (qBase <= 0 || qEvent <= 0) return 0.0

        val dBase = abs(iBaseHist - tauBase) / qBase
        val dEvent = abs(iEventHist - tauEvent) / qEvent
        val raw = minOf(dBase, dEvent)

        val sqrtN = sqrt(nEff.toDouble())
        val penalty = sqrtN / (sqrtN + 10.0)
        return (raw * penalty).coerceIn(0.0, 1.0)
    }
}
