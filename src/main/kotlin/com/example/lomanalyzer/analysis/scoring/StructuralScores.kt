/*
 * НАЗНАЧЕНИЕ
 * Расчёт трёх оценок ОСИ 1 — «структурное влияние» автора (этап 7 алгоритма,
 * docs/algorithm.md; формулы Приложения Е.4.1). Эти оценки характеризуют
 * «вес» аккаунта вне зависимости от конкретной темы: размер аудитории,
 * возраст аккаунта и фоновую вовлечённость.
 *
 * ЧТО ВНУТРИ
 * object StructuralScores с тремя чистыми функциями:
 *  - aud(followers)             — Aud_a, логарифмически сжатый размер аудитории;
 *  - age(days, maxDays)         — Age_a, нормированный возраст аккаунта;
 *  - erBg(reactions, followers) — ER_a^bg, фоновая вовлечённость (engagement rate).
 *
 * МЕТОД
 * Aud_a = ln(1 + F_a)               — log-сжатие, чтобы хвост крупных аккаунтов
 *                                     не доминировал (см. Е.4.1).
 * Age_a = (возраст) / (макс. возраст по сессии) — приведение к диапазону [0..1].
 * ER_a^bg = (1/|B_a|) Σ (L+C+R)/F   — формула engagement rate Bonsón & Ratkai [105]:
 *                                     средняя доля реакций (лайки+комменты+репосты)
 *                                     от числа подписчиков по фоновым постам B_a.
 *
 * БИБЛИОТЕКИ
 * Только stdlib Kotlin (kotlin.math.ln) — внешних зависимостей нет.
 *
 * СВЯЗИ
 * Вызывается из ScoringExecutor (этап 7); результаты сохраняются в LomScores
 * и далее z-нормализуются в CompositeScorer (ось 1 композита Struct_a).
 */
package com.example.lomanalyzer.analysis.scoring

import kotlin.math.ln

/**
 * Оценки структурного влияния (диплом Е.4, ось 1).
 *
 * Три оценки, вычисляемые из профиля автора и его фоновой активности:
 * - Aud_a:    log-сжатое число подписчиков (точечная оценка);
 * - Age_a:    нормированный «возраст» аккаунта (точечная оценка);
 * - ER_a^bg:  фоновый engagement rate по формуле Bonsón & Ratkai [105] (выборочная оценка).
 */
object StructuralScores {

    /**
     * Aud_a = ln(1 + F_a) — логарифмически сжатый размер аудитории.
     *
     * Сдвиг на +1 нужен, чтобы при F_a = 0 получить ln(1) = 0 и избежать ln(0) = -∞.
     * @param followers F_a — число подписчиков автора
     * @return ln(1 + F_a)
     * @see Appendix E.4.1
     */
    fun aud(followers: Int): Double = ln(1.0 + followers)

    /**
     * Age_a = (d_a - d_a^created) / max_b(d_b - d_b^created).
     *
     * Возраст аккаунта, нормированный на максимальный возраст среди всех авторов
     * сессии → значение в [0..1] (1.0 у самого «старого» аккаунта).
     * @param accountAgeDays дней с момента создания аккаунта автора a
     * @param maxAccountAgeDays максимум возраста по всем авторам сессии (знаменатель нормировки)
     * @return нормированный возраст в [0..1]
     * @see Appendix E.4.1
     */
    fun age(accountAgeDays: Long, maxAccountAgeDays: Long): Double {
        // Защита от деления на ноль/некорректных данных: если максимум не задан — 0.0
        if (maxAccountAgeDays <= 0) return 0.0
        // Нормировка возраста относительно самого старого аккаунта сессии
        return accountAgeDays.toDouble() / maxAccountAgeDays.toDouble()
    }

    /**
     * ER_a^bg = (1/|B_a|) * Σ_{i ∈ B_a} (L_i + C_i^cnt + R_i) / F_a.
     *
     * Фоновый engagement rate по формуле Bonsón & Ratkai: средняя по фоновым
     * постам B_a доля суммарных реакций (лайки + комментарии + репосты) от числа
     * подписчиков F_a.
     * @param reactions список значений (L + C + R) по каждому фоновому посту
     * @param followers F_a — число подписчиков (знаменатель доли реакций)
     * @return средний относительный отклик; 0.0 при отсутствии постов или F_a ≤ 0
     * @see Appendix E.4.1
     */
    fun erBg(reactions: List<Int>, followers: Int): Double {
        // Нет фоновых постов или некорректное число подписчиков → оценка не определена, возвращаем 0
        if (reactions.isEmpty() || followers <= 0) return 0.0
        // Суммируем реакции по всем фоновым постам (Long — защита от переполнения Int)
        val sum = reactions.sumOf { it.toLong() }
        // Усреднение по числу постов |B_a| и нормировка на подписчиков F_a:
        // sum / (|B_a| * F_a) эквивалентно (1/|B_a|) * Σ (L+C+R)/F
        return sum.toDouble() / (reactions.size.toDouble() * followers)
    }
}
