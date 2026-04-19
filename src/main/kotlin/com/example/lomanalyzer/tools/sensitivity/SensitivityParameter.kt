package com.example.lomanalyzer.tools.sensitivity

/**
 * Sensitivity analysis parameters per v6 §28.
 * Each parameter has a default, low, and high value for the sensitivity range.
 */
data class SensitivityParameter(
    val name: String,
    val defaultValue: Double,
    val lowValue: Double,
    val highValue: Double,
    val category: String,
)

object SensitivityParameters {
    val ALL: List<SensitivityParameter> = listOf(
        // Topic filtering
        SensitivityParameter("topic_threshold_full", 0.30, 0.20, 0.40, "TOPIC"),
        SensitivityParameter("topic_threshold_fallback", 0.33, 0.25, 0.45, "TOPIC"),
        SensitivityParameter("l1_weight", 0.40, 0.30, 0.50, "TOPIC"),
        SensitivityParameter("l2_weight", 0.60, 0.50, 0.70, "TOPIC"),
        // Gamma
        SensitivityParameter("gamma_clip_lo", 0.25, 0.20, 0.30, "GAMMA"),
        SensitivityParameter("gamma_clip_hi", 0.65, 0.55, 0.75, "GAMMA"),
        SensitivityParameter("gamma_fallback", 0.45, 0.40, 0.50, "GAMMA"),
        SensitivityParameter("gamma_min_active", 20.0, 15.0, 30.0, "GAMMA"),
        SensitivityParameter("gamma_min_r2", 0.05, 0.03, 0.10, "GAMMA"),
        // Normalization
        SensitivityParameter("iqr_min", 0.001, 0.0005, 0.01, "NORM"),
        SensitivityParameter("cv_iqr_unstable", 0.25, 0.20, 0.30, "NORM"),
        SensitivityParameter("cv_iqr_severe", 0.35, 0.30, 0.40, "NORM"),
        // Bootstrap
        SensitivityParameter("bootstrap_outer", 300.0, 100.0, 500.0, "BOOTSTRAP"),
        SensitivityParameter("bootstrap_inner", 100.0, 30.0, 200.0, "BOOTSTRAP"),
        // Scoring weights
        SensitivityParameter("a_weight", 0.55, 0.45, 0.65, "SCORING"),
        SensitivityParameter("e_weight", 0.45, 0.35, 0.55, "SCORING"),
        SensitivityParameter("wT_setB", 0.15, 0.10, 0.20, "SCORING"),
        SensitivityParameter("wV_setB", 0.30, 0.25, 0.35, "SCORING"),
        SensitivityParameter("wS_setB", 0.35, 0.30, 0.40, "SCORING"),
        SensitivityParameter("wO_setB", 0.20, 0.15, 0.25, "SCORING"),
        // Anomaly detection
        SensitivityParameter("volume_z_threshold", 2.5, 2.0, 3.0, "ANOMALY"),
        SensitivityParameter("tone_z_threshold", 2.0, 1.5, 2.5, "ANOMALY"),
        SensitivityParameter("tone_min_posts", 3.0, 2.0, 5.0, "ANOMALY"),
        SensitivityParameter("rolling_window", 7.0, 5.0, 14.0, "ANOMALY"),
        SensitivityParameter("sigma_min_volume", 1.0, 0.5, 2.0, "ANOMALY"),
        SensitivityParameter("sigma_min_tone", 0.05, 0.02, 0.10, "ANOMALY"),
        // Risk
        SensitivityParameter("risk_multi_coeff", 1.2, 1.0, 1.5, "RISK"),
        SensitivityParameter("risk_boundary_lo", 0.15, 0.10, 0.20, "RISK"),
        SensitivityParameter("risk_boundary_mid", 0.35, 0.30, 0.40, "RISK"),
        SensitivityParameter("risk_boundary_hi", 0.55, 0.50, 0.60, "RISK"),
        // Dedup
        SensitivityParameter("jaccard_threshold", 0.75, 0.65, 0.85, "DEDUP"),
        SensitivityParameter("dedup_window_hours", 72.0, 24.0, 168.0, "DEDUP"),
        SensitivityParameter("exact_min_length", 30.0, 20.0, 50.0, "DEDUP"),
        // Reference
        SensitivityParameter("gamma_div_ok", 0.10, 0.05, 0.15, "REFERENCE"),
        SensitivityParameter("gamma_div_mild", 0.20, 0.15, 0.25, "REFERENCE"),
        SensitivityParameter("tau_ref_base", 0.78, 0.70, 0.85, "REFERENCE"),
        // Role
        SensitivityParameter("confidence_penalty_k", 10.0, 5.0, 20.0, "ROLE"),
        SensitivityParameter("confidence_block_threshold", 0.25, 0.15, 0.35, "ROLE"),
        // Originality
        SensitivityParameter("orig_repost_comment_w", 0.5, 0.3, 0.7, "ORIGINALITY"),
        SensitivityParameter("orig_media_only_w", 0.25, 0.15, 0.35, "ORIGINALITY"),
        // Sentiment
        SensitivityParameter("low_confidence_threshold", 0.15, 0.10, 0.20, "SENTIMENT"),
        SensitivityParameter("huber_k", 1.345, 1.0, 1.5, "SENTIMENT"),
        SensitivityParameter("tone_mixed_sd", 0.50, 0.40, 0.60, "SENTIMENT"),
        // Discovery
        SensitivityParameter("discovery_repost_threshold", 50.0, 30.0, 100.0, "DISCOVERY"),
        SensitivityParameter("discovery_max_authors", 30.0, 20.0, 50.0, "DISCOVERY"),
    )
}
