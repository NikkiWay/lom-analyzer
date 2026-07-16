/*
 * НАЗНАЧЕНИЕ
 * Профильный детектор near-дубликатов диплома (Stage 3 дедупликации, см.
 * docs/algorithm.md): нормализованное расстояние Левенштейна (Levenshtein) по
 * последовательностям лемм. Порог сходства 0.90, временное окно ±72 ч.
 *
 * ЧТО ВНУТРИ
 * class NormalizedLevenshtein: проверка пригодности поста (isEligible), расчёт
 * нормализованного сходства (computeSimilarity), проверка временного окна
 * (isWithinWindow), агрегирующий тест на near-дубликат (isNearDuplicate) и
 * приватный расчёт дистанции Левенштейна (levenshteinDistance).
 *
 * МЕТОД
 * Нормализованное сходство = 1 − editDistance(A,B) / max(|A|,|B|), где
 * editDistance — расстояние Левенштейна, считаемое по токенам (леммам), а не по
 * символам. Два поста — near-дубликаты, если сходство >= порога (0.90) И они
 * попадают в одно временное окно (±72 ч). Дистанция вычисляется с оптимизацией
 * по памяти O(min(m,n)) — две бегущие строки матрицы.
 *
 * БИБЛИОТЕКИ
 * kotlin.math.max/min/abs; внешних зависимостей нет.
 *
 * СВЯЗИ
 * Используется DedupPipeline (runStage2) для попарного сравнения кандидатов.
 */
package com.example.lomanalyzer.analysis.dedup

import kotlin.math.max
import kotlin.math.min

/**
 * Near-duplicate detection using normalized Levenshtein distance on lemma sequences.
 * Diploma stage 3: threshold 0.90, temporal window ±72h.
 *
 * Normalized similarity = 1 - editDistance(A, B) / max(|A|, |B|)
 * Posts are near-duplicates when similarity >= threshold.
 *
 * Детектор near-дубликатов по нормализованному расстоянию Левенштейна на леммах.
 * @param threshold порог сходства [0..1]; при >= считается near-дубликатом (0.90 по диплому).
 * @param windowHours ширина временного окна в часах (сравниваются только посты внутри окна).
 */
class NormalizedLevenshtein(
    private val threshold: Float = 0.90f,
    private val windowHours: Long = 72,
) {
    companion object {
        /** Минимальная длина собственного текста для участия в near-дедупликации. */
        private const val MIN_TEXT_LENGTH = 100
    }

    /**
     * Пригоден ли пост к сравнению: достаточно длинный И тематически релевантный.
     */
    fun isEligible(ownTextLength: Int, isTopicRelevant: Boolean?): Boolean =
        ownTextLength >= MIN_TEXT_LENGTH && isTopicRelevant == true

    /**
     * Нормализованное сходство двух текстов по их леммам.
     * @return 1f — оба пусты (идентичны); 0f — ровно один пуст; иначе 1 − dist/maxLen в [0..1].
     */
    fun computeSimilarity(lemmasA: List<String>, lemmasB: List<String>): Float {
        // Два пустых текста считаем идентичными; ровно один пуст — полностью различны
        if (lemmasA.isEmpty() && lemmasB.isEmpty()) return 1f
        if (lemmasA.isEmpty() || lemmasB.isEmpty()) return 0f
        // Знаменатель нормализации — длина более длинной последовательности лемм
        val maxLen = max(lemmasA.size, lemmasB.size)
        // Абсолютное число правок (вставки/удаления/замены лемм)
        val dist = levenshteinDistance(lemmasA, lemmasB)
        // Перевод абсолютной дистанции в сходство [0..1]
        return 1f - dist.toFloat() / maxLen.toFloat()
    }

    /**
     * Проверяет, попадают ли два поста в одно временное окно (±windowHours).
     * Время трактуется как секунды (Unix epoch).
     */
    fun isWithinWindow(publishedAtA: Long, publishedAtB: Long): Boolean {
        val diffSeconds = kotlin.math.abs(publishedAtA - publishedAtB)
        return diffSeconds <= windowHours * 3600
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
        // Вне временного окна пару не сравниваем
        if (!isWithinWindow(publishedAtA, publishedAtB)) return false to 0f
        val sim = computeSimilarity(lemmasA, lemmasB)
        return (sim >= threshold) to sim
    }

    /**
     * Standard Levenshtein edit distance on token sequences.
     * Uses O(min(m,n)) space optimization.
     *
     * Классическое расстояние Левенштейна по последовательностям токенов (лемм)
     * с оптимизацией памяти O(min(m,n)): храним только две строки DP-матрицы.
     * @return минимальное число вставок/удалений/замен для превращения a в b.
     */
    private fun levenshteinDistance(a: List<String>, b: List<String>): Int {
        val m = a.size
        val n = b.size
        // Крайние случаи: одна из последовательностей пуста
        if (m == 0) return n
        if (n == 0) return m

        // Ensure a is shorter for space optimization
        // Делаем более короткую последовательность «short» — экономим память DP-строки
        val (short, long) = if (m <= n) a to b else b to a
        val sLen = short.size
        val lLen = long.size

        // prev/curr — предыдущая и текущая строки матрицы редактирования
        var prev = IntArray(sLen + 1) { it }
        var curr = IntArray(sLen + 1)

        // Заполняем DP-матрицу построчно по длинной последовательности
        for (i in 1..lLen) {
            curr[0] = i
            for (j in 1..sLen) {
                // Стоимость замены: 0, если токены совпадают, иначе 1
                val cost = if (long[i - 1] == short[j - 1]) 0 else 1
                // Минимум из удаления, вставки и замены
                curr[j] = min(min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost)
            }
            // Меняем строки местами: curr становится prev для следующей итерации
            val tmp = prev
            prev = curr
            curr = tmp
        }
        // Итоговая дистанция — в последней вычисленной строке (после swap это prev)
        return prev[sLen]
    }
}
