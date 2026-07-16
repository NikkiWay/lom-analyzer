/*
 * НАЗНАЧЕНИЕ
 * Расчёт двух оценок ОСИ 4 — «отклик аудитории» на тематические посты автора
 * (этап 7 алгоритма, docs/algorithm.md; формулы Приложения Е.4.4). Описывает,
 * как аудитория реагирует на тематический контент автора: интенсивность
 * вовлечённости и тональность комментариев.
 *
 * ЧТО ВНУТРИ
 * object ResponseScores с двумя функциями:
 *  - erTop(reactions, followers)   — ER_a^top, тематический engagement rate;
 *  - resp(commentSentiments)       — Resp_a, распределение тональности комментариев.
 *
 * МЕТОД
 * ER_a^top = (1/|T_a|) Σ_{i∈T_a} (L+C+R)/F — та же формула Bonsón & Ratkai, что и
 *            ER_a^bg, но по тематическим постам T_a (а не фоновым).
 * Resp_a = (q+, q0, q-), q_a^k = Σ_i |{j∈C_i : r_j=k}| / Σ_i |C_i| — доли
 *          комментариев каждой тональности по всем тематическим постам.
 * ВАЖНО: комментарии имеют КЛАСТЕРНУЮ структуру (сгруппированы по постам C_i),
 * поэтому именно для Resp_a применяется двухуровневый bootstrap 300×100 (Е.3.2).
 *
 * БИБЛИОТЕКИ
 * Stdlib Kotlin; тип результата — core.SentimentDistribution.
 *
 * СВЯЗИ
 * Вызывается из ScoringExecutor; ER_a^top и Resp_a сохраняются в LomScores.
 * Resp_a используется в RoleAssigner.audienceResponse и в двухуровневом bootstrap.
 */
package com.example.lomanalyzer.analysis.scoring

import com.example.lomanalyzer.core.SentimentDistribution

/**
 * Оценки отклика аудитории (диплом Е.4, ось 4).
 *
 * Две оценки:
 * - ER_a^top: тематический engagement rate (та же формула Bonsón & Ratkai, что и
 *   ER_bg, но по тематическим постам);
 * - Resp_a:   распределение тональности комментариев (кластеризованы по постам).
 *
 * @see Appendix E.4.4
 */
object ResponseScores {

    /**
     * ER_a^top = (1/|T_a|) * Σ_{i ∈ T_a} (L_i + C_i^cnt + R_i) / F_a.
     *
     * Тематический engagement rate: средняя по тематическим постам T_a доля
     * реакций (лайки + комментарии + репосты) от числа подписчиков F_a.
     * @param reactions список значений (L + C + R) по каждому тематическому посту
     * @param followers F_a — число подписчиков
     * @return средний относительный отклик; 0.0 при отсутствии постов или F_a ≤ 0
     * @see Appendix E.4.4
     */
    fun topicalEngagementRate(reactions: List<Int>, followers: Int): Double {
        // Нет тематических постов или некорректное F_a → оценка не определена, 0
        if (reactions.isEmpty() || followers <= 0) return 0.0
        // Суммируем реакции по тематическим постам (Long — защита от переполнения)
        val sum = reactions.sumOf { it.toLong() }
        // Усреднение по |T_a| и нормировка на F_a: sum / (|T_a| * F_a)
        return sum.toDouble() / (reactions.size.toDouble() * followers)
    }

    /**
     * Resp_a = (q_a^+, q_a^0, q_a^-),
     * где q_a^k = Σ_{i ∈ T_a} |{j ∈ C_i : r_j = k}| / Σ_{i ∈ T_a} |C_i|.
     *
     * Распределение тональности комментариев по всем тематическим постам автора.
     * Имеет **кластерную структуру** (комментарии сгруппированы по постам), что
     * существенно для двухуровневого bootstrap на этапе 6 (Е.3.2).
     *
     * @param commentSentiments список меток тональности ВСЕХ комментариев под тем. постами автора
     * @return SentimentDistribution {positive, neutral, negative}
     * @see Appendix E.4.4
     */
    fun audienceResponseDistribution(commentSentiments: List<String>): SentimentDistribution {
        // Нет комментариев → отклик не определён, считаем нейтральным (0,1,0)
        if (commentSentiments.isEmpty()) return SentimentDistribution(0.0, 1.0, 0.0)

        // Знаменатель — суммарное число комментариев Σ|C_i| по всем тем. постам
        val n = commentSentiments.size.toDouble()
        // Считаем позитивные и негативные комментарии (регистр меток игнорируем)
        val posCount = commentSentiments.count { it.equals("POSITIVE", ignoreCase = true) }
        val negCount = commentSentiments.count { it.equals("NEGATIVE", ignoreCase = true) }
        // Нейтральные — остаток (включая прочие/неизвестные метки)
        val neuCount = commentSentiments.size - posCount - negCount

        // Доли q_a^k тональностей комментариев; сумма компонент = 1
        return SentimentDistribution(
            positive = posCount / n,
            neutral = neuCount / n,
            negative = negCount / n,
        )
    }

    /**
     * Вычисляет Resp_a усреднением распределений вероятностей по комментариям
     * (мягкое голосование) — см. PositionScore.authorPositionFromProbabilities.
     *
     * @param commentDistributions распределения по каждому комментарию под
     *   тематическими постами автора.
     * @return Resp_a как среднее распределение; (0, 1, 0) при отсутствии комментариев.
     */
    fun audienceResponseFromProbabilities(
        commentDistributions: List<SentimentDistribution>,
    ): SentimentDistribution {
        // Нет комментариев → отклик не определён, считаем нейтральным
        if (commentDistributions.isEmpty()) return SentimentDistribution(0.0, 1.0, 0.0)

        val n = commentDistributions.size.toDouble()
        return SentimentDistribution(
            positive = commentDistributions.sumOf { it.positive } / n,
            neutral = commentDistributions.sumOf { it.neutral } / n,
            negative = commentDistributions.sumOf { it.negative } / n,
        )
    }
}
