/*
 * НАЗНАЧЕНИЕ
 * Вспомогательный фильтр исключения для этапа 6 (двухпроходная тематическая
 * фильтрация, см. docs/algorithm.md). Отвечает на единственный вопрос: попал ли
 * пост под «стоп-набор» исключающих n-грамм (excluded n-grams), которые сразу
 * выводят публикацию из рассмотрения вне зависимости от совпадений по ключевым
 * словам.
 *
 * ЧТО ВНУТРИ
 * object ExclusionFilter с одним методом isExcluded — тонкая обёртка над уже
 * вычисленным результатом сопоставления n-грамм (NgramMatchResult).
 *
 * СВЯЗИ
 * Работает в паре с NgramMatcher (который выставляет флаг excludedHit) и
 * TopicRelevanceFilter (где исключённые посты получают стратум EXCLUDED).
 *
 * БИБЛИОТЕКИ
 * Только stdlib Kotlin, внешних зависимостей нет.
 */
package com.example.lomanalyzer.analysis.topic

/** Stateless-проверка признака исключения поста по исключающим n-граммам. */
object ExclusionFilter {
    /**
     * Сообщает, исключён ли пост из тематической выборки.
     * @param matchResult результат сопоставления лемм поста с n-граммами.
     * @return true, если сработала хотя бы одна исключающая n-грамма.
     */
    fun isExcluded(matchResult: NgramMatchResult): Boolean =
        matchResult.excludedHit
}
