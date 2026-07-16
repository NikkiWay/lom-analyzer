/*
 * НАЗНАЧЕНИЕ
 * Каталог параметров для анализа чувствительности (sensitivity analysis):
 * перечень настраиваемых констант алгоритма с их значениями по умолчанию и
 * границами «низкое/высокое» для перебора. Инструмент валидации устойчивости
 * результатов к выбору параметров (см. диплом 2.2.8, docs/algorithm.md).
 *
 * ЧТО ВНУТРИ
 * data class SensitivityParameter — описание одного параметра (имя, default, low,
 * high, категория). object SensitivityParameters.ALL — полный список параметров,
 * сгруппированных по категориям: робастная статистика, bootstrap, дедуп,
 * достаточность данных, сбор, тематическая фильтрация, тональность, оригинальность.
 *
 * МЕТОД
 * При sensitivity analysis каждый параметр поочерёдно ставится в low/high, а
 * влияние на итог (роли, риск, аномалии) оценивается в SensitivityHarness.
 * Ссылки на формулы: Huber (E.2), bootstrap (E.3.1–E.3.3) — см. docs/formulas.md.
 *
 * СВЯЗИ
 * Используется SensitivityHarness для прогона и отчёта.
 */
package com.example.lomanalyzer.tools.sensitivity

/**
 * Описание одного параметра анализа чувствительности.
 *
 * @property name машинное имя параметра.
 * @property defaultValue значение по умолчанию (базовый прогон).
 * @property lowValue нижняя граница диапазона перебора.
 * @property highValue верхняя граница диапазона перебора.
 * @property category категория параметра (для группировки в отчёте).
 */
data class SensitivityParameter(
    val name: String,
    val defaultValue: Double,
    val lowValue: Double,
    val highValue: Double,
    val category: String,
)

/** Полный каталог параметров, участвующих в анализе чувствительности. */
object SensitivityParameters {
    /** Список всех параметров с их диапазонами low/high, сгруппированных по категориям. */
    val ALL: List<SensitivityParameter> = listOf(
        // Huber M-estimator (E.2)
        SensitivityParameter("huber_k", 1.345, 1.0, 1.7, "ROBUST_STATS"),

        // One-level bootstrap (E.3.1)
        SensitivityParameter("bootstrap_B", 1000.0, 500.0, 2000.0, "BOOTSTRAP"),

        // Two-level bootstrap (E.3.2)
        SensitivityParameter("bootstrap_B_outer", 300.0, 100.0, 1000.0, "BOOTSTRAP"),
        SensitivityParameter("bootstrap_B_inner", 100.0, 100.0, 1000.0, "BOOTSTRAP"),

        // Confidence level (E.3.3)
        SensitivityParameter("confidence_level", 0.95, 0.90, 0.99, "BOOTSTRAP"),

        // Dedup threshold
        SensitivityParameter("levenshtein_threshold", 0.90, 0.80, 0.95, "DEDUP"),
        SensitivityParameter("dedup_window_hours", 72.0, 24.0, 168.0, "DEDUP"),

        // Sufficiency thresholds
        SensitivityParameter("sufficiency_min_topic_posts_reliable", 10.0, 5.0, 20.0, "SUFFICIENCY"),
        SensitivityParameter("sufficiency_min_comments_reliable", 50.0, 20.0, 100.0, "SUFFICIENCY"),
        SensitivityParameter("sufficiency_max_ci_width_reliable", 0.20, 0.10, 0.30, "SUFFICIENCY"),

        // Background window
        SensitivityParameter("baseline_window_days", 60.0, 30.0, 120.0, "COLLECTION"),

        // Topic filtering
        SensitivityParameter("semantic_threshold", 0.55, 0.40, 0.70, "TOPIC"),
        SensitivityParameter("confident_l1_threshold", 0.50, 0.30, 0.70, "TOPIC"),

        // Sentiment
        SensitivityParameter("low_confidence_threshold", 0.15, 0.10, 0.20, "SENTIMENT"),

        // Originality weights
        SensitivityParameter("orig_repost_comment_w", 0.5, 0.3, 0.7, "ORIGINALITY"),
    )
}
