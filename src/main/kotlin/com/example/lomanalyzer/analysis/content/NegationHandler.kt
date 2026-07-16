/*
 * НАЗНАЧЕНИЕ
 * Учёт отрицаний при словарном анализе тональности (sentiment). Инвертирует
 * полярность лемм, попавших в окно после русского отрицания («не хороший» -> негатив).
 * Вспомогательный компонент для DictionarySentiment (fallback-режим NLP, см. 2.2.7).
 *
 * ЧТО ВНУТРИ
 *  - NegationAdjusted — результат: скорректированные счётчики позитивных/негативных лемм;
 *  - NegationHandler — класс с методом applyNegation() и наборами негаторов.
 *
 * МЕТОД
 *  Скользящее окно: после каждого негатора помечаются следующие windowSize лемм (1..3).
 *  Помеченная позитивная лемма засчитывается как негативная, и наоборот.
 *
 * БИБЛИОТЕКИ
 *  Только stdlib Kotlin — внешних зависимостей нет.
 */
package com.example.lomanalyzer.analysis.content

/**
 * Результат корректировки счётчиков тональности с учётом отрицаний.
 *
 * @param positiveCount число позитивных лемм после инверсии.
 * @param negativeCount число негативных лемм после инверсии.
 * @param negationApplied true, если хотя бы одна лемма была инвертирована отрицанием.
 */
data class NegationAdjusted(
    val positiveCount: Int,
    val negativeCount: Int,
    val negationApplied: Boolean,
)

/**
 * Инверсия тональности в окне из 1-3 токенов после отрицания.
 * Негаторы: не, ни, нет, без, нельзя, никогда.
 *
 * @param windowSize размер окна влияния отрицания (число следующих лемм).
 * @param negators множество слов-отрицаний.
 */
class NegationHandler(
    val windowSize: Int = 3,
    val negators: Set<String> = DEFAULT_NEGATORS,
) {
    companion object {
        /** Стандартный набор русских отрицаний. */
        val DEFAULT_NEGATORS = setOf("не", "ни", "нет", "без", "нельзя", "никогда")
        /** Расширенный набор: добавляет ослабляющие/отрицающие слова (едва, вряд, невозможно). */
        val EXTENDED_NEGATORS = DEFAULT_NEGATORS + setOf("едва", "вряд", "невозможно")
    }

    /**
     * Пересчитывает позитивные/негативные леммы с учётом инверсии отрицаниями.
     *
     * @param lemmas леммы текста.
     * @param posSet множество позитивных лемм словаря.
     * @param negSet множество негативных лемм словаря.
     * @return NegationAdjusted со скорректированными счётчиками.
     */
    fun applyNegation(
        lemmas: List<String>,
        posSet: Set<String>,
        negSet: Set<String>,
    ): NegationAdjusted {
        var posCount = 0
        var negCount = 0
        var negApplied = false

        // Позиции лемм, на которые влияет отрицание (попадают в окно после негатора)
        val negatedPositions = mutableSetOf<Int>()

        // Помечаем позиции в окне windowSize после каждого негатора
        for ((i, lemma) in lemmas.withIndex()) {
            if (lemma.lowercase() in negators) {
                for (j in (i + 1)..minOf(i + windowSize, lemmas.size - 1)) {
                    negatedPositions.add(j)
                }
            }
        }

        // Считаем тональные леммы; если лемма под отрицанием — её полярность инвертируется
        for ((i, lemma) in lemmas.withIndex()) {
            val lower = lemma.lowercase()
            val isNegated = i in negatedPositions

            when {
                lower in posSet -> {
                    // Позитив под отрицанием становится негативом
                    if (isNegated) { negCount++; negApplied = true }
                    else posCount++
                }
                lower in negSet -> {
                    // Негатив под отрицанием становится позитивом
                    if (isNegated) { posCount++; negApplied = true }
                    else negCount++
                }
            }
        }

        return NegationAdjusted(posCount, negCount, negApplied)
    }
}
