/*
 * НАЗНАЧЕНИЕ
 * Индикатор достаточности данных по каждому автору (этап 8/качество, docs/algorithm.md;
 * диплом 2.1.5). Помечает, насколько можно доверять рассчитанным оценкам автора,
 * исходя из объёма данных и ширины доверительных интервалов (CI).
 *
 * ЧТО ВНУТРИ
 *  - data class SufficiencyThresholds — настраиваемые пороги (с дефолтами из диплома);
 *  - object DataSufficiencyIndicator.evaluate(...) — присваивает уровень достаточности.
 *
 * МЕТОД / АЛГОРИТМ
 * Три уровня (DataSufficiency): RELIABLE / PRELIMINARY / UNRELIABLE.
 *  - UNRELIABLE, если нарушен любой «жёсткий» минимум (мало тем. постов/комментариев
 *    или слишком широкий CI);
 *  - RELIABLE, если выполнены все «строгие» условия (достаточный объём И узкий CI);
 *  - PRELIMINARY — всё промежуточное.
 * Ширина CI берётся из bootstrap-оценок (Е.3); чем шире интервал, тем менее надёжна оценка.
 *
 * БИБЛИОТЕКИ
 * Stdlib Kotlin; уровень — enum core.DataSufficiency.
 *
 * СВЯЗИ
 * Использует объёмы из этапа 7 (число тем. постов/комментариев) и ширину CI из
 * этапа bootstrap; результат отображается в UI/экспорте как метка доверия.
 */
package com.example.lomanalyzer.analysis.sufficiency

import com.example.lomanalyzer.core.DataSufficiency

/**
 * Индикатор достаточности данных по автору (диплом 2.1.5).
 *
 * Три уровня в зависимости от объёма данных и ширины CI:
 * - RELIABLE:    данных достаточно для уверенных выводов;
 * - PRELIMINARY: промежуточный уровень;
 * - UNRELIABLE:  данных слишком мало, чтобы доверять оценкам.
 *
 * Пороги настраиваемы, но имеют обоснованные значения по умолчанию из диплома.
 */
data class SufficiencyThresholds(
    /** Минимум тематических постов для уровня RELIABLE. */
    val minTopicPostsReliable: Int = 10,
    /** Минимум комментариев для уровня RELIABLE. */
    val minCommentsReliable: Int = 50,
    /** Максимально допустимая ширина CI для уровня RELIABLE. */
    val maxCiWidthReliable: Double = 0.20,
    /** Жёсткий минимум тематических постов: ниже него — UNRELIABLE. */
    val minTopicPostsUnreliable: Int = 3,
    /** Жёсткий минимум комментариев: ниже него — UNRELIABLE. */
    val minCommentsUnreliable: Int = 10,
    /** Предельная ширина CI: шире неё — UNRELIABLE. */
    val maxCiWidthUnreliable: Double = 0.50,
)

object DataSufficiencyIndicator {

    /**
     * Оценивает достаточность данных для одного автора.
     *
     * @param topicPostCount |T_a| — число тематических постов
     * @param commentCount Σ|C_i| — суммарное число комментариев по всем тем. постам
     * @param avgCiWidth средняя ширина 95%-CI для выборочных оценок (null, если CI нет)
     * @param thresholds пороги (по умолчанию — значения из диплома)
     * @return уровень достаточности RELIABLE / PRELIMINARY / UNRELIABLE
     */
    fun evaluate(
        topicPostCount: Int,
        commentCount: Int,
        avgCiWidth: Double?,
        thresholds: SufficiencyThresholds = SufficiencyThresholds(),
    ): DataSufficiency {
        // UNRELIABLE, если нарушен хотя бы один жёсткий минимум объёма/ширины CI
        if (topicPostCount < thresholds.minTopicPostsUnreliable) return DataSufficiency.UNRELIABLE
        if (commentCount < thresholds.minCommentsUnreliable) return DataSufficiency.UNRELIABLE
        if (avgCiWidth != null && avgCiWidth > thresholds.maxCiWidthUnreliable) return DataSufficiency.UNRELIABLE

        // Условие достаточного ОБЪЁМА: и постов, и комментариев не меньше «строгих» минимумов
        val volumeOk = topicPostCount >= thresholds.minTopicPostsReliable &&
            commentCount >= thresholds.minCommentsReliable
        // Условие по ТОЧНОСТИ: CI отсутствует либо достаточно узкий
        val ciOk = avgCiWidth == null || avgCiWidth <= thresholds.maxCiWidthReliable

        // RELIABLE только при одновременном выполнении объёма и точности
        if (volumeOk && ciOk) return DataSufficiency.RELIABLE

        // Всё, что между жёстким минимумом и строгими условиями — предварительная оценка
        return DataSufficiency.PRELIMINARY
    }
}
