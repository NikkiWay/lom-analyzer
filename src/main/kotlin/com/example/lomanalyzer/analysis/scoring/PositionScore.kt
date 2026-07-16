/*
 * НАЗНАЧЕНИЕ
 * Расчёт оценки ОСИ 3 — «позиция автора» по теме (этап 7 алгоритма,
 * docs/algorithm.md; формула Приложения Е.4.3). Показывает эмоциональную
 * окраску собственных тематических постов автора (за/нейтрально/против).
 *
 * ЧТО ВНУТРИ
 * object PositionScore с единственной функцией pos(sentiments): по списку
 * sentiment-меток тематических постов строит распределение Pos_a.
 *
 * МЕТОД
 * Pos_a = (p_a^+, p_a^0, p_a^-), где p_a^k = |{i ∈ T_a : s_i = k}| / |T_a| —
 * доли постов с тональностью positive/neutral/negative. Сумма компонент = 1.
 * Метки тональности s_i присваивает NLP-модуль (sidecar dostoevsky/rubert,
 * см. architecture.md); здесь — только агрегация в доли.
 *
 * БИБЛИОТЕКИ
 * Stdlib Kotlin; тип результата — core.SentimentDistribution.
 *
 * СВЯЗИ
 * Вызывается из ScoringExecutor; компоненты Pos_a сохраняются в LomScores и
 * затем используются в RoleAssigner.authorPosition для атрибута «позиция».
 */
package com.example.lomanalyzer.analysis.scoring

import com.example.lomanalyzer.core.SentimentDistribution

/**
 * Оценка позиции автора (диплом Е.4, ось 3).
 *
 * Pos_a = (p_a^+, p_a^0, p_a^-),
 * где p_a^k = |{i ∈ T_a : s_i = k}| / |T_a|.
 *
 * s_i — категория тональности, присвоенная NLP-модулем.
 * Сумма компонент = 1.
 *
 * @see Appendix E.4.3
 */
object PositionScore {

    /**
     * Вычисляет Pos_a по меткам тональности тематических постов автора.
     * @param sentiments список категорий тональности по каждому тематическому посту
     * @return SentimentDistribution с долями (positive, neutral, negative)
     */
    fun authorPositionDistribution(sentiments: List<String>): SentimentDistribution {
        // Нет тематических постов → позиция не определена, считаем её нейтральной (0,1,0)
        if (sentiments.isEmpty()) return SentimentDistribution(0.0, 1.0, 0.0)

        // Знаменатель долей — общее число тематических постов |T_a|
        val n = sentiments.size.toDouble()
        // Считаем посты с позитивной и негативной тональностью (регистр меток игнорируем)
        val posCount = sentiments.count { it.equals("POSITIVE", ignoreCase = true) }
        val negCount = sentiments.count { it.equals("NEGATIVE", ignoreCase = true) }
        // Нейтральные — всё, что не позитив и не негатив (включая прочие/неизвестные метки)
        val neuCount = sentiments.size - posCount - negCount

        // Переводим счётчики в доли p_a^k; сумма компонент по построению = 1
        return SentimentDistribution(
            positive = posCount / n,
            neutral = neuCount / n,
            negative = negCount / n,
        )
    }
}
