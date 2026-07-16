/*
 * НАЗНАЧЕНИЕ
 * Альтернативный детектор near-дубликатов на основе ограниченного (bounded)
 * коэффициента Жаккара (Jaccard) по биграммам лемм. Часть дедупликации (этап
 * обработки, см. docs/algorithm.md). Применяется только к длинным тематическим
 * постам (ownTextLength >= 100) ради производительности.
 *
 * ЧТО ВНУТРИ
 * class BoundedJaccard: проверка пригодности поста (isEligible), расчёт
 * Jaccard-сходства по биграммам (computeSimilarity), проверка временного окна
 * (isWithinWindow), агрегирующий тест на near-дубликат (isNearDuplicate) и
 * построение множества биграмм (toBigrams).
 *
 * МЕТОД
 * Jaccard(A,B) = |A ∩ B| / |A ∪ B|, где A,B — множества словесных биграмм
 * (n-gram, n=2) из лемм. Два поста считаются near-дубликатами, если сходство
 * >= порога (0.75) И они попадают в одно временное окно (±72 ч). Профильный
 * детектор диплома — NormalizedLevenshtein (порог 0.90); этот класс —
 * вспомогательный/оптимизационный вариант.
 *
 * БИБЛИОТЕКИ
 * kotlin.math.abs; внешних зависимостей нет.
 */
package com.example.lomanalyzer.analysis.dedup

import kotlin.math.abs

/**
 * Stage 2: Bounded Jaccard near-duplicate detection.
 * Applied ONLY to topical posts with ownTextLength >= 100 (performance optimization).
 * Uses word-level bigrams on lemmas; window ±72 hours; threshold 0.75.
 *
 * Детектор near-дубликатов по ограниченному Jaccard для длинных тематических постов.
 * @param threshold порог сходства Jaccard [0..1]; при >= считается near-дубликатом.
 * @param windowHours ширина временного окна в часах (сравниваются только посты внутри окна).
 */
class BoundedJaccard(
    private val threshold: Float = 0.75f,
    private val windowHours: Long = 72,
) {
    companion object {
        /** Минимальная длина собственного текста для применения Jaccard. */
        private const val MIN_TEXT_LENGTH = 100
    }

    /**
     * Пригоден ли пост к сравнению: достаточно длинный И тематически релевантный.
     */
    fun isEligible(ownTextLength: Int, isTopicRelevant: Boolean?): Boolean =
        ownTextLength >= MIN_TEXT_LENGTH && isTopicRelevant == true

    /**
     * Считает Jaccard-сходство двух текстов по множествам их биграмм.
     * @return 1f — два пустых текста; 0f — один пуст; иначе |∩|/|∪| в [0..1].
     */
    @Suppress("ReturnCount")
    fun computeSimilarity(lemmasA: List<String>, lemmasB: List<String>): Float {
        // Превращаем последовательности лемм в множества словесных биграмм
        val bigramsA = toBigrams(lemmasA)
        val bigramsB = toBigrams(lemmasB)

        // Оба пусты — считаем идентичными; ровно один пуст — полностью различны
        if (bigramsA.isEmpty() && bigramsB.isEmpty()) return 1f
        if (bigramsA.isEmpty() || bigramsB.isEmpty()) return 0f

        // Числитель — мощность пересечения, знаменатель — мощность объединения
        val intersection = bigramsA.intersect(bigramsB).size
        val union = bigramsA.union(bigramsB).size

        return if (union > 0) intersection.toFloat() / union else 0f
    }

    /**
     * Проверяет, попадают ли два поста в одно временное окно (±windowHours).
     * Примечание: время трактуется как миллисекунды (diffMs).
     */
    fun isWithinWindow(publishedAtA: Long, publishedAtB: Long): Boolean {
        val diffMs = abs(publishedAtA - publishedAtB)
        val windowMs = windowHours * 3600 * 1000
        return diffMs <= windowMs
    }

    /**
     * Комплексная проверка пары на near-дубликат: окно + порог сходства.
     * @return (признак near-дубликата, значение сходства).
     */
    fun isNearDuplicate(
        lemmasA: List<String>,
        lemmasB: List<String>,
        publishedAtA: Long,
        publishedAtB: Long,
    ): Pair<Boolean, Float> {
        // Вне временного окна сравнение не проводим
        if (!isWithinWindow(publishedAtA, publishedAtB)) return false to 0f
        val sim = computeSimilarity(lemmasA, lemmasB)
        return (sim >= threshold) to sim
    }

    /**
     * Строит множество словесных биграмм (соседних пар лемм).
     * При длине < 2 возвращает множество отдельных лемм.
     */
    private fun toBigrams(lemmas: List<String>): Set<String> {
        if (lemmas.size < 2) return lemmas.toSet()
        return lemmas.windowed(2).map { "${it[0]} ${it[1]}" }.toSet()
    }
}
