/*
 * НАЗНАЧЕНИЕ
 * Декларативное описание таблицы "nlp_result" — кэш результатов NLP по хэшу
 * текста и версии модели. Позволяет не пересчитывать леммы/тональность для
 * повторяющихся или ранее обработанных текстов (ускорение этапа NLP).
 *
 * ЧТО ВНУТРИ
 * Один object-таблица NlpResults (Exposed ORM). Ключ кэша — пара (text_hash,
 * model_version) с уникальным индексом. На сессию/пост напрямую не ссылается
 * (кэш межсессионный).
 *
 * ФРЕЙМВОРКИ
 * Exposed ORM — IntIdTable (суррогатный primary key "id"). uniqueIndex объявлен
 * в init { }. Результаты пишет Python sidecar или Kotlin-fallback.
 *
 * СВЯЗИ
 * Внешних ключей нет — связь логическая через хэш текста (text_hash).
 */
package com.example.lomanalyzer.storage.tables

import org.jetbrains.exposed.dao.id.IntIdTable

/**
 * Таблица-кэш результатов NLP ("nlp_result").
 *
 * IntIdTable (суррогатный primary key "id"). Хранит результаты обработки текста,
 * адресуемые по хэшу текста и версии модели.
 */
object NlpResults : IntIdTable("nlp_result") {
    /** Хэш исходного текста — ключ кэша (одинаковый текст обрабатывается один раз). */
    val textHash = text("text_hash")
    /** Версия NLP-модели, которой получен результат; по умолчанию "v1". */
    val modelVersion = text("model_version").default("v1")
    /** Леммы текста в виде JSON-массива; может отсутствовать. */
    val lemmasJson = text("lemmas_json").nullable()
    /** Метка тональности {+, 0, -}; может отсутствовать. */
    val sentiment = text("sentiment").nullable()
    /** Числовая оценка тональности; может отсутствовать. */
    val score = float("score").nullable()
    /** Метод получения результата (модель/fallback); может отсутствовать. */
    val method = text("method").nullable()
    /** Момент сохранения результата в кэш (Unix-время, мс). */
    // Распределение вероятностей модели; NULL для словарного fallback (см. V13).
    /** Вероятность позитивного класса [0..1]; NULL, если источник её не даёт. */
    val probPositive = float("prob_positive").nullable()
    /** Вероятность нейтрального класса [0..1]; NULL, если источник её не даёт. */
    val probNeutral = float("prob_neutral").nullable()
    /** Вероятность негативного класса [0..1]; NULL, если источник её не даёт. */
    val probNegative = float("prob_negative").nullable()

    val createdAt = long("created_at")

    init {
        // Уникальность пары (хэш текста, версия модели) — один кэш-результат на текст и версию
        uniqueIndex(textHash, modelVersion)
    }
}
