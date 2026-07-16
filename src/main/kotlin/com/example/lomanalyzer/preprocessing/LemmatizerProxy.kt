/*
 * НАЗНАЧЕНИЕ
 * Прокси-лемматизатор: приводит токены к нормальной форме на этапе препроцессинга
 * (этап 5, docs/algorithm.md). Реализует двухрежимность NLP-модуля (architecture.md, 2.2.7):
 * при доступном NLP-сервисе (Python sidecar, pymorphy3 — полноценная lemmatization)
 * делегирует ему, иначе использует Kotlin-fallback на основе Snowball stemmer (Lucene/
 * Tartarus RussianStemmer) — это стемминг (отсечение окончаний), не полная лемматизация.
 *
 * ЧТО ВНУТРИ
 *  - LemmaResult — результат по одному токену (позиция, исходная форма, лемма/стем);
 *  - LemmatizerProxy — класс: suspend lemmatize() (выбор режима), stemFallback() (Kotlin),
 *    приватный stem() (потокобезопасный вызов stemmer).
 *
 * БИБЛИОТЕКИ
 *  - org.tartarus.snowball.ext.RussianStemmer (Snowball/Lucene) — fallback-стеммер;
 *  - NlpService — абстракция NLP (Python sidecar или Kotlin-реализация).
 *
 * СВЯЗИ
 *  Вызывается из PreprocessingExecutor и LocalKotlinNlpService.
 */
package com.example.lomanalyzer.preprocessing

import com.example.lomanalyzer.nlp.NlpService
import org.tartarus.snowball.ext.RussianStemmer

/**
 * Результат лемматизации одного токена.
 *
 * @param position индекс токена в исходной последовательности.
 * @param original исходная словоформа.
 * @param lemma нормальная форма (лемма) или стем (в fallback-режиме).
 */
data class LemmaResult(
    val position: Int,
    val original: String,
    val lemma: String,
)

/**
 * Лемматизатор с двумя режимами работы.
 * @param nlpService NLP-сервис для полноценной lemmatization; если null — Kotlin-fallback (Snowball).
 */
class LemmatizerProxy(
    private val nlpService: NlpService? = null,
) {
    /** Snowball-стеммер русского языка для fallback-режима. */
    private val stemmer = RussianStemmer()

    /**
     * Лемматизирует токены. Если задан nlpService — делегирует ему (полная lemmatization),
     * иначе — стемминг через Snowball.
     */
    suspend fun lemmatize(tokens: List<String>): List<LemmaResult> {
        if (nlpService != null) {
            // Основной режим: собираем текст и передаём в NLP-сервис (Python sidecar)
            val text = tokens.joinToString(" ")
            val result = nlpService.lemmatize(text)
            // Сопоставляем леммы с исходными токенами по позиции (если токена нет — берём лемму)
            return result.lemmas.mapIndexed { i, lemma ->
                LemmaResult(i, tokens.getOrElse(i) { lemma }, lemma)
            }
        }
        // Fallback: NLP-сервис недоступен — стеммим в Kotlin
        return stemFallback(tokens)
    }

    /** Fallback-стемминг каждого токена через Snowball; нелитерные токены оставляются как есть. */
    fun stemFallback(tokens: List<String>): List<LemmaResult> =
        tokens.mapIndexed { index, token ->
            // Стеммим только токены, начинающиеся с буквы (числа/пунктуацию не трогаем)
            val stem = if (token.isNotEmpty() && token.first().isLetter()) {
                stem(token)
            } else {
                token
            }
            LemmaResult(position = index, original = token, lemma = stem)
        }

    /** Потокобезопасный вызов Snowball-стеммера (stemmer хранит состояние, поэтому synchronized). */
    private fun stem(word: String): String {
        synchronized(stemmer) {
            stemmer.current = word.lowercase()
            stemmer.stem()
            return stemmer.current
        }
    }
}
