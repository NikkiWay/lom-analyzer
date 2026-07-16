/*
 * НАЗНАЧЕНИЕ
 * Первый проход (L1) двухпроходной тематической фильтрации (этап 6,
 * docs/algorithm.md): сопоставление лемматизированного текста поста с тремя
 * наборами n-грамм — основными (primary), дополнительными (secondary) и
 * исключающими (excluded). Результат сопоставления далее превращается в балл L1
 * в TopicRelevanceFilter.
 *
 * ЧТО ВНУТРИ
 * - data class NgramMatchResult: число попаданий по primary/secondary и флаг
 *   срабатывания исключающей n-граммы.
 * - class NgramMatcher: метод match (подсчёт попаданий) и приватный containsNgram
 *   (проверка наличия одной n-граммы среди лемм поста).
 *
 * МЕТОД
 * n-грамма задана как список лемм. Для однословной n-граммы — прямая проверка
 * вхождения. Для многословной применяется «ослабленное» (relaxed) сопоставление:
 * требуется присутствие ВСЕХ лемм n-граммы в тексте (без требования точного
 * порядка следования), потому что разные лемматизаторы (pymorphy3 vs Snowball)
 * могут по-разному разбивать и переупорядочивать словоформы.
 *
 * СВЯЗИ
 * Наборы n-грамм готовит TopicFilterExecutor (лемматизирует тем же
 * лемматизатором, что и посты). Результат потребляют TopicRelevanceFilter и
 * ExclusionFilter.
 *
 * БИБЛИОТЕКИ
 * Только stdlib Kotlin.
 */
package com.example.lomanalyzer.analysis.topic

/**
 * Результат сопоставления лемм поста с наборами n-грамм.
 *
 * @param primaryHits число сработавших основных n-грамм.
 * @param secondaryHits число сработавших дополнительных n-грамм.
 * @param excludedHit true, если сработала хотя бы одна исключающая n-грамма
 *   (в этом случае hits обнуляются — пост подлежит исключению).
 */
data class NgramMatchResult(
    val primaryHits: Int,
    val secondaryHits: Int,
    val excludedHit: Boolean,
)

/**
 * Сопоставитель n-грамм для L1-прохода тематической фильтрации.
 *
 * @param primaryNgrams основные n-граммы (каждая — список лемм).
 * @param secondaryNgrams дополнительные n-граммы (дают меньший вес в L1).
 * @param excludedNgrams исключающие n-граммы (срабатывание = немедленное исключение).
 */
class NgramMatcher(
    private val primaryNgrams: List<List<String>>,
    private val secondaryNgrams: List<List<String>>,
    private val excludedNgrams: List<List<String>>,
) {
    /**
     * Сопоставляет леммы поста со всеми наборами n-грамм.
     * @param lemmas леммы текста поста.
     * @return число попаданий по primary/secondary и флаг исключения.
     */
    fun match(lemmas: List<String>): NgramMatchResult {
        // Приводим леммы к нижнему регистру для регистронезависимого сравнения
        val lowerLemmas = lemmas.map { it.lowercase() }

        // Сначала проверяем исключающие n-граммы: при первом же попадании пост
        // считается исключённым, дальнейший подсчёт совпадений не имеет смысла
        if (excludedNgrams.any { containsNgram(lowerLemmas, it) }) {
            return NgramMatchResult(0, 0, excludedHit = true)
        }

        // Считаем, сколько основных и дополнительных n-грамм встретилось в тексте
        val primaryHits = primaryNgrams.count { containsNgram(lowerLemmas, it) }
        val secondaryHits = secondaryNgrams.count { containsNgram(lowerLemmas, it) }

        return NgramMatchResult(primaryHits, secondaryHits, excludedHit = false)
    }

    /**
     * Проверяет, присутствует ли одна n-грамма среди лемм поста.
     * @return true, если n-грамма «найдена» (см. правила relaxed-сопоставления).
     */
    @Suppress("ReturnCount")
    private fun containsNgram(lemmas: List<String>, ngram: List<String>): Boolean {
        // Пустая n-грамма не может совпасть
        if (ngram.isEmpty()) return false
        // Однословная n-грамма: простая проверка вхождения леммы
        if (ngram.size == 1) return ngram[0].lowercase() in lemmas
        // Многословная n-грамма: relaxed-сопоставление — требуем наличие ВСЕХ
        // лемм n-граммы (без точного порядка), т.к. лемматизация может по-разному
        // переупорядочивать/разбивать слова в pymorphy3 и Snowball
        val target = ngram.map { it.lowercase() }
        return target.all { it in lemmas }
    }
}
