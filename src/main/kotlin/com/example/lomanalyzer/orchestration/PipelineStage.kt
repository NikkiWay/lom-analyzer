package com.example.lomanalyzer.orchestration

sealed class PipelineStage(val order: Int, val name: String) {
    data object AppStartup : PipelineStage(1, "APP_STARTUP")
    data object SessionInit : PipelineStage(2, "SESSION_INIT")
    data object VkAuth : PipelineStage(3, "VK_AUTH")
    data object NlpInit : PipelineStage(4, "NLP_INIT")
    data object ResourceValidation : PipelineStage(5, "RESOURCE_VALIDATION")
    data object TransitionAnalyzing : PipelineStage(6, "TRANSITION_ANALYZING")
    data object CollectBaseline : PipelineStage(7, "COLLECT_BASELINE")
    data object CollectCurrent : PipelineStage(8, "COLLECT_CURRENT")
    data object CollectReposters : PipelineStage(9, "COLLECT_REPOSTERS")
    data object Discovery : PipelineStage(10, "DISCOVERY")
    data object NormRecalc : PipelineStage(11, "NORM_RECALC")
    data object Preprocessing : PipelineStage(12, "PREPROCESSING")
    data object TopicFiltering : PipelineStage(13, "TOPIC_FILTERING")
    data object AnalystValidation : PipelineStage(14, "ANALYST_VALIDATION")
    data object Deduplication : PipelineStage(15, "DEDUPLICATION")
    data object OriginalityClassification : PipelineStage(16, "ORIGINALITY_CLASSIFICATION")
    data object GammaCalibration : PipelineStage(17, "GAMMA_CALIBRATION")
    data object MReachRecalc : PipelineStage(18, "M_REACH_RECALC")
    data object BaselineScoring : PipelineStage(19, "BASELINE_SCORING")
    data object OrthogonalizationDecision : PipelineStage(20, "ORTHOGONALIZATION_DECISION")
    data object CurrentScoring : PipelineStage(21, "CURRENT_SCORING")
    data object ReferenceCalibration : PipelineStage(22, "REFERENCE_CALIBRATION")
    data object RoleClassification : PipelineStage(23, "ROLE_CLASSIFICATION")
    data object OptionalGmm : PipelineStage(24, "OPTIONAL_GMM")
    data object ContentAnalysis : PipelineStage(25, "CONTENT_ANALYSIS")
    data object AccountFlags : PipelineStage(26, "ACCOUNT_FLAGS")
    data object SeasonalNormalization : PipelineStage(27, "SEASONAL_NORMALIZATION")
    data object AnomalyDetection : PipelineStage(28, "ANOMALY_DETECTION")
    data object AnomalyDeduplication : PipelineStage(29, "ANOMALY_DEDUPLICATION")
    data object RiskScoring : PipelineStage(30, "RISK_SCORING")
    data object PersonaAggregation : PipelineStage(31, "PERSONA_AGGREGATION")
    data object SessionQuality : PipelineStage(32, "SESSION_QUALITY")
    data object Persist : PipelineStage(33, "PERSIST")
    data object PublishToUi : PipelineStage(34, "PUBLISH_TO_UI")
    data object Export : PipelineStage(35, "EXPORT")

    companion object {
        val allStages: List<PipelineStage> = listOf(
            AppStartup, SessionInit, VkAuth, NlpInit, ResourceValidation,
            TransitionAnalyzing, CollectBaseline, CollectCurrent, CollectReposters,
            Discovery, NormRecalc, Preprocessing, TopicFiltering,
            AnalystValidation, Deduplication, OriginalityClassification,
            GammaCalibration, MReachRecalc, BaselineScoring,
            OrthogonalizationDecision, CurrentScoring, ReferenceCalibration,
            RoleClassification, OptionalGmm, ContentAnalysis, AccountFlags,
            SeasonalNormalization, AnomalyDetection, AnomalyDeduplication,
            RiskScoring, PersonaAggregation, SessionQuality, Persist,
            PublishToUi, Export,
        )
    }
}
