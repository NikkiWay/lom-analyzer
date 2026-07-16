/*
 * НАЗНАЧЕНИЕ
 * Декларативное описание таблицы "sentiment_result" — тональность (сентимент)
 * поста: метка {+, 0, -}, числовая оценка и метод вычисления. Заполняется на
 * этапе анализа содержания и используется при расчёте позиции автора (ось 3)
 * и отклика аудитории (ось 4).
 *
 * ЧТО ВНУТРИ
 * Один object-таблица SentimentResults (Exposed ORM). Связь "один-к-одному" с
 * post через post_id (primary key + foreign key + uniqueIndex). Хранит также
 * служебные поля бутстрапа сентимента (согласованность вариантов).
 *
 * МЕТОД
 * Тональность определяется NLP-моделью (Python sidecar: dostoevsky / rubert)
 * либо словарным fallback (DictionarySentiment). Бутстрап-поля фиксируют
 * устойчивость метки при ресемплинге (этап 6, бутстрап сентимента).
 *
 * ФРЕЙМВОРКИ
 * Exposed ORM — обычная Table, естественный ключ post_id (foreign key на Posts).
 *
 * СВЯЗИ
 * post_id -> post.id (Posts.kt). Соседняя таблица processed_text держит леммы.
 */
package com.example.lomanalyzer.storage.tables

import org.jetbrains.exposed.sql.Table

/**
 * Таблица тональности постов ("sentiment_result").
 *
 * Обычная Table с естественным primary key post_id (он же foreign key на post).
 * Связь с постом — один-к-одному.
 */
object SentimentResults : Table("sentiment_result") {
    /** foreign key на post.id; primary key и uniqueIndex — одна оценка тональности на пост. */
    val postId = reference("post_id", Posts).uniqueIndex()
    /** Метка тональности: положительная "+", нейтральная "0" или отрицательная "-". */
    val sentiment = text("sentiment")
    /** Числовая оценка уверенности/полярности модели; может отсутствовать. */
    val score = float("score").nullable()
    /** Метод определения тональности (например, модель sidecar или словарный fallback). */
    val method = text("method")
    /** Признак, что применялась обработка отрицаний (negation handling); по умолчанию false. */
    val negationApplied = bool("negation_applied").default(false)
    /** Доля согласованных меток при бутстрапе сентимента [0..1] — устойчивость результата; может отсутствовать. */
    val bootstrapAgreement = float("bootstrap_agreement").nullable()
    /** Распределение меток по бутстрап-вариантам (JSON); может отсутствовать. */
    val bootstrapVariants = text("bootstrap_variants").nullable()

    /** primary key таблицы — post_id (связь один-к-одному с постом). */
    override val primaryKey = PrimaryKey(postId)
}
