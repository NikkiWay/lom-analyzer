/*
 * НАЗНАЧЕНИЕ
 * Сводка качества двухпроходной тематической фильтрации (между этапами 6 и 7,
 * docs/algorithm.md): агрегирует результаты по стратумам и формирует короткий
 * список спорных постов для ручной проверки аналитиком.
 *
 * ЧТО ВНУТРИ
 * - data class FilterQualitySummary: счётчики по стратумам, производные доли
 *   (confidentRatio/pass2Ratio/disputedRatio) и список спорных постов.
 * - object FilterQualityComputer: метод compute — построение сводки по
 *   проскоренным постам.
 *
 * СВЯЗИ
 * Вызывается из TopicFilterExecutor после простановки релевантности всем постам.
 * Использует TopicStratum (категории) и ValidationPost (модель поста для аналитика).
 *
 * БИБЛИОТЕКИ
 * Только stdlib Kotlin.
 */
package com.example.lomanalyzer.analysis.topic

/**
 * Summary of topic filtering quality (diploma 2.1.1, between stages 6 and 7).
 *
 * Three main proportions:
 * - confidentRatio: fraction of posts that confidently passed first pass
 * - pass2Ratio: fraction that required second pass (RuBERT)
 * - disputedRatio: fraction classified as disputed (borderline or rejected)
 *
 * Plus a list of first 30 disputed posts for analyst review.
 *
 * Сводка качества тематической фильтрации (этап 6). Хранит абсолютные счётчики по
 * стратумам и список первых ~30 спорных постов для ручной проверки.
 *
 * @param totalPosts всего проскоренных постов.
 * @param confidentCount число уверенно принятых на проходе 1 (CONFIDENT).
 * @param pass2ConfirmedCount число подтверждённых проходом 2 RuBERT (PASS2_CONFIRMED).
 * @param disputedCount число спорных/отклонённых (DISPUTED).
 * @param excludedCount число исключённых исключающими n-граммами (EXCLUDED).
 * @param disputedPosts выборка спорных постов для аналитика.
 */
data class FilterQualitySummary(
    val totalPosts: Int,
    val confidentCount: Int,
    val pass2ConfirmedCount: Int,
    val disputedCount: Int,
    val excludedCount: Int,
    val disputedPosts: List<ValidationPost>,
) {
    /** Доля уверенно принятых постов (CONFIDENT) от общего числа. */
    val confidentRatio: Float get() = if (totalPosts > 0) confidentCount.toFloat() / totalPosts else 0f
    /** Доля постов, потребовавших проход 2 (RuBERT) и подтверждённых им. */
    val pass2Ratio: Float get() = if (totalPosts > 0) pass2ConfirmedCount.toFloat() / totalPosts else 0f
    /** Доля спорных/отклонённых постов. */
    val disputedRatio: Float get() = if (totalPosts > 0) disputedCount.toFloat() / totalPosts else 0f
}

/** Построитель сводки качества фильтрации по результатам скоринга постов. */
object FilterQualityComputer {
    /**
     * Build summary from scored posts.
     * @param scoredPosts pairs of (postId, TopicScoreResult)
     * @param postLookup function to build ValidationPost from postId
     * @param maxDisputed max disputed posts to include in the list (default 30)
     *
     * Строит сводку по проскоренным постам.
     * @param scoredPosts пары (id поста, результат скоринга).
     * @param postLookup функция загрузки ValidationPost по id поста.
     * @param maxDisputed сколько спорных постов включать в список (по умолчанию 30).
     */
    fun compute(
        scoredPosts: List<Pair<Int, TopicScoreResult>>,
        postLookup: (Int) -> ValidationPost?,
        maxDisputed: Int = 30,
    ): FilterQualitySummary {
        // Счётчики по каждому стратуму
        var confident = 0
        var pass2Confirmed = 0
        var disputed = 0
        var excluded = 0
        // Идентификаторы спорных постов (накапливаем не более maxDisputed)
        val disputedPostIds = mutableListOf<Int>()

        // Один проход по всем результатам: инкрементируем счётчик нужного стратума
        for ((postId, result) in scoredPosts) {
            when (result.stratum) {
                TopicStratum.CONFIDENT -> confident++
                TopicStratum.PASS2_CONFIRMED -> pass2Confirmed++
                TopicStratum.DISPUTED -> {
                    disputed++
                    // Запоминаем первые maxDisputed спорных постов для аналитика
                    if (disputedPostIds.size < maxDisputed) disputedPostIds.add(postId)
                }
                TopicStratum.EXCLUDED -> excluded++
            }
        }

        // Подгружаем модели спорных постов (несуществующие отбрасываем)
        val disputedPosts = disputedPostIds.mapNotNull { postLookup(it) }

        return FilterQualitySummary(
            totalPosts = scoredPosts.size,
            confidentCount = confident,
            pass2ConfirmedCount = pass2Confirmed,
            disputedCount = disputed,
            excludedCount = excluded,
            disputedPosts = disputedPosts,
        )
    }
}
