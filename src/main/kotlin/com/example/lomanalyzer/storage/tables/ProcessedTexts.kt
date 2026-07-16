/*
 * НАЗНАЧЕНИЕ
 * Декларативное описание таблицы "processed_text" — результаты лингвистического
 * препроцессинга поста (этап 3: лемматизация, выделение сущностей, очистка
 * текста, определение языка). Один пост — одна строка результата.
 *
 * ЧТО ВНУТРИ
 * Один object-таблица ProcessedTexts (Exposed ORM). Связь "один-к-одному" с
 * таблицей post через колонку post_id, которая одновременно является primary
 * key и foreign key, плюс uniqueIndex (гарантия не более одного результата
 * на пост).
 *
 * ФРЕЙМВОРКИ
 * Exposed ORM — здесь используется обычная Table (без суррогатного id), так как
 * естественный ключ — это post_id. reference(...) задаёт foreign key на Posts.
 * NLP-результаты кладёт сюда Python sidecar либо Kotlin-fallback (этап 3).
 *
 * СВЯЗИ
 * post_id -> post.id (Posts.kt). Параллельная таблица sentiment_result хранит
 * тональность по тому же ключу post_id.
 */
package com.example.lomanalyzer.storage.tables

import org.jetbrains.exposed.sql.Table

/**
 * Таблица результатов препроцессинга текста поста ("processed_text").
 *
 * Использует обычную Table: естественный primary key — post_id (он же foreign
 * key на post). Связь с постом — один-к-одному.
 */
object ProcessedTexts : Table("processed_text") {
    /** foreign key на post.id; одновременно primary key и uniqueIndex — ровно один результат препроцессинга на пост. */
    val postId = reference("post_id", Posts).uniqueIndex()
    /** Леммы поста в виде JSON-массива (нормальные формы слов после лемматизации); может отсутствовать. */
    val lemmasJson = text("lemmas_json").nullable()
    /** Именованные сущности (NER) в виде JSON; может отсутствовать. */
    val entitiesJson = text("entities_json").nullable()
    /** Определённый язык текста (например, "ru"); может отсутствовать. */
    val language = text("language").nullable()
    /** Очищенный текст (без URL, разметки, мусора) для дальнейшего анализа; может отсутствовать. */
    val cleanText = text("clean_text").nullable()

    /** primary key таблицы — post_id (связь один-к-одному с постом). */
    override val primaryKey = PrimaryKey(postId)
}
