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
 * Pos_a = (p_a^+, p_a^0, p_a^-), сумма компонент = 1. Агрегация двухвариантная,
 * по тому, что даёт источник тональности:
 *
 *  - authorPositionFromProbabilities — среднее распределений вероятностей по
 *    постам (мягкое голосование). Основной путь: модель sidecar возвращает
 *    вероятности всех классов.
 *  - authorPositionDistribution — доли меток, p_a^k = |{i ∈ T_a : s_i = k}| / |T_a|.
 *    Путь для словарного fallback, который вероятностей не даёт.
 *
 * Оба возвращают распределение с единичной суммой и взаимозаменяемы для
 * последующих этапов.
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

    /**
     * Вычисляет Pos_a усреднением распределений вероятностей по тематическим
     * постам автора (мягкое голосование).
     *
     * Модель тональности возвращает вероятности всех классов, и метка-победитель
     * их огрубляет: пост с распределением neutral 0.80 / positive 0.15 и пост с
     * neutral 0.80 / negative 0.15 дают одну и ту же метку «neutral», хотя
     * склонности у них противоположные. Особенно это заметно у авторов с одним
     * тематическим постом: по меткам Pos_a вырождается ровно в (0, 1, 0).
     *
     * Усреднение сохраняет перевес: сумма компонент остаётся равной 1, так как
     * каждое слагаемое — распределение с единичной суммой.
     *
     * @param distributions распределения по каждому тематическому посту автора.
     * @return Pos_a как среднее распределение; (0, 1, 0) при отсутствии постов.
     */
    fun authorPositionFromProbabilities(
        distributions: List<SentimentDistribution>,
    ): SentimentDistribution {
        // Нет тематических постов → позиция не определена, считаем её нейтральной
        if (distributions.isEmpty()) return SentimentDistribution(0.0, 1.0, 0.0)

        val n = distributions.size.toDouble()
        return SentimentDistribution(
            positive = distributions.sumOf { it.positive } / n,
            neutral = distributions.sumOf { it.neutral } / n,
            negative = distributions.sumOf { it.negative } / n,
        )
    }
}
