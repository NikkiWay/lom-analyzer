/*
 * НАЗНАЧЕНИЕ
 * DTO-модель одного поста, отправляемого на ручную проверку аналитику в рамках
 * контроля качества тематической фильтрации (этап 6, см. docs/algorithm.md).
 * Используется прежде всего для списка «спорных» (DISPUTED) постов и для
 * валидации precision/recall по голосам аналитика.
 *
 * ЧТО ВНУТРИ
 * data class ValidationPost — снимок поста: текст, итоговый тематический score,
 * признак системной релевантности, стратум фильтрации, идентификаторы автора и
 * владельца стены, голос аналитика и временные/оконные атрибуты.
 *
 * СВЯЗИ
 * Формируется в TopicFilterExecutor (через FilterQualityComputer), потребляется
 * BayesBetaValidator (голоса аналитика) и UI ручной валидации.
 *
 * БИБЛИОТЕКИ
 * Только stdlib Kotlin.
 */
package com.example.lomanalyzer.analysis.topic

/**
 * Снимок поста для ручной валидации тематической фильтрации.
 *
 * @param id внутренний идентификатор поста в БД.
 * @param text исходный текст поста (для показа аналитику).
 * @param score итоговый тематический балл (combined) в диапазоне [0..1].
 * @param systemRelevant решение системы: признан ли пост тематически релевантным.
 * @param stratum имя стратума фильтрации (CONFIDENT / PASS2_CONFIRMED / DISPUTED / EXCLUDED).
 * @param authorName отображаемое имя автора (опционально).
 * @param communityName название сообщества/стены (опционально).
 * @param fromVkId VK-идентификатор автора публикации.
 * @param ownerVkId VK-идентификатор владельца стены.
 * @param analystVote голос аналитика: true=релевантен, false=нет, null=не размечен.
 * @param publishedAt время публикации (Unix epoch, секунды).
 * @param window окно наблюдения: CURRENT (тематическое) или BASELINE (фоновое).
 */
data class ValidationPost(
    val id: Int,
    val text: String,
    val score: Float,
    val systemRelevant: Boolean,
    val stratum: String,
    val authorName: String = "",
    val communityName: String = "",
    val fromVkId: Int = 0,
    val ownerVkId: Int = 0,
    val analystVote: Boolean? = null,
    val publishedAt: Long = 0,
    val window: String = "CURRENT",
)
