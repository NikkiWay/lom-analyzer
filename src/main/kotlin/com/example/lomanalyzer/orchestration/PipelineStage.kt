/*
 * НАЗНАЧЕНИЕ
 * Определяет 10 стадий пайплайна анализа и их порядок. Это «скелет» оркестрации:
 * PipelineOrchestrator пробегает список стадий по очереди, для каждой вызывает
 * зарегистрированный исполнитель (см. PipelineWiring). Стадии соответствуют
 * этапам 1–10 из docs/algorithm.md (подраздел 2.1.1 диплома) и пяти фазам
 * с контрольными точками PHASE_1..PHASE_5.
 *
 * ЧТО ВНУТРИ
 * sealed class PipelineStage с десятью data object-стадиями (каждая хранит
 * порядковый номер order и строковое имя name). companion object.allStages —
 * упорядоченный список всех стадий, по которому идёт оркестратор.
 *
 * МЕТОД
 * Соответствие стадий фазам и контрольным точкам:
 *   Фаза 1 (PHASE_1): SESSION_INIT
 *   Фаза 2 (PHASE_2): DATA_COLLECTION (сбор A→B→C)
 *   Фаза 3 (PHASE_3): PREPROCESSING, TOPIC_FILTERING
 *   Фаза 4 (PHASE_4): SCORING, BOOTSTRAP
 *   Фаза 5 (PHASE_5): COMPOSITE_ROLES, QUALITY_CHECK
 *   Финализация: EXPORT, PUBLISH_TO_UI
 *
 * СВЯЗИ
 * Имя стадии (name) служит ключом при регистрации исполнителя в
 * PipelineOrchestrator.registerExecutor и при поиске исполнителя в orchestrate.
 */
package com.example.lomanalyzer.orchestration

/**
 * Ten pipeline stages matching the algorithm phases from diploma section 2.1.1.
 * Десять стадий пайплайна, соответствующих фазам алгоритма (раздел 2.1.1 диплома).
 *
 * @property order порядковый номер стадии (1..10) — задаёт очерёдность выполнения.
 * @property name строковое имя стадии — ключ для регистрации и поиска исполнителя.
 *
 * Phase 1: Task setup         -> SESSION_INIT
 * Phase 2: Data collection    -> DATA_COLLECTION
 * Phase 3: Data processing    -> PREPROCESSING, TOPIC_FILTERING
 * Phase 4: Scores & inference -> SCORING, BOOTSTRAP
 * Phase 5: Classification     -> COMPOSITE_ROLES, QUALITY_CHECK
 * Finalization                -> EXPORT, PUBLISH_TO_UI
 */
sealed class PipelineStage(val order: Int, val name: String) {
    /** Stage 1: session creation, VK auth, NLP init */
    data object SessionInit : PipelineStage(1, "SESSION_INIT")

    /** Stage 2-4: collect topic posts, author registry, author data (profile, background, comments) */
    data object DataCollection : PipelineStage(2, "DATA_COLLECTION")

    /** Stage 5: text cleaning, lemmatization, language detection */
    data object Preprocessing : PipelineStage(3, "PREPROCESSING")

    /** Stage 6: two-pass topic filtering + deduplication */
    data object TopicFiltering : PipelineStage(4, "TOPIC_FILTERING")

    /** Stage 7: compute 11 quantitative scores across 4 axes */
    data object Scoring : PipelineStage(5, "SCORING")

    /** Stage 8: robust stats + bootstrap (one-level and two-level) */
    data object Bootstrap : PipelineStage(6, "BOOTSTRAP")

    /** Stage 9: composite scores, adaptive thresholds, role assignment */
    data object CompositeRoles : PipelineStage(7, "COMPOSITE_ROLES")

    /** Stage 10: data sufficiency indicator + session quality indicators */
    data object QualityCheck : PipelineStage(8, "QUALITY_CHECK")

    /** Export results to CSV/JSON */
    data object Export : PipelineStage(9, "EXPORT")

    /** Notify UI that results are ready */
    data object PublishToUi : PipelineStage(10, "PUBLISH_TO_UI")

    companion object {
        /**
         * Полный упорядоченный список всех 10 стадий — основа цикла оркестрации.
         * Ленивая инициализация (by lazy): список собирается один раз при первом
         * обращении.
         */
        val allStages: List<PipelineStage> by lazy {
            listOf(
                SessionInit, DataCollection, Preprocessing, TopicFiltering,
                Scoring, Bootstrap, CompositeRoles, QualityCheck,
                Export, PublishToUi,
            )
        }
    }
}
