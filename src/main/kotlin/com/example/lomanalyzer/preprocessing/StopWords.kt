/*
 * НАЗНАЧЕНИЕ
 * Список русских стоп-слов и фильтрация по нему. Используется на этапе препроцессинга
 * (этап 5, см. docs/algorithm.md) для удаления незначимых слов (предлоги, союзы и т. п.)
 * перед лемматизацией/анализом, чтобы они не зашумляли частотные метрики (TF-IDF, тематику).
 *
 * ЧТО ВНУТРИ
 *  object StopWords — singleton: ленивое чтение словаря из ресурса, проверка isStopWord(),
 *  фильтрация списка токенов filter(), доступ ко всему множеству getAll().
 *
 * БИБЛИОТЕКИ
 *  Только stdlib Kotlin. Словарь читается из classpath-ресурса /stopwords/ru.txt.
 */
package com.example.lomanalyzer.preprocessing

/** Singleton-хранилище русских стоп-слов и операций фильтрации. */
object StopWords {
    /** Множество стоп-слов, лениво загружаемое из ресурса /stopwords/ru.txt (строки с # — комментарии). */
    private val wordSet: Set<String> by lazy {
        val stream = StopWords::class.java.getResourceAsStream("/stopwords/ru.txt")
        // Читаем построчно, нормализуем регистр, отбрасываем пустые строки и комментарии
        stream?.bufferedReader()?.readLines()
            ?.map { it.trim().lowercase() }
            ?.filter { it.isNotBlank() && !it.startsWith("#") }
            ?.toSet()
            ?: emptySet()
    }

    /** true, если слово (без учёта регистра) является стоп-словом. */
    fun isStopWord(word: String): Boolean =
        word.lowercase() in wordSet

    /** Возвращает токены без стоп-слов. */
    fun filter(tokens: List<String>): List<String> =
        tokens.filter { !isStopWord(it) }

    /** Полное множество стоп-слов (например, для диагностики). */
    fun getAll(): Set<String> = wordSet
}
