/*
 * НАЗНАЧЕНИЕ
 * DAO (слой доступа к данным) для обработанных текстов постов (таблица
 * processed_texts) — результат NLP/препроцессинга (этап 3): леммы, именованные
 * сущности, определённый язык и очищенный текст, привязанные к посту (postId).
 * Обмен между модулями — только через SQLite.
 *
 * ЧТО ВНУТРИ
 * Класс ProcessedTextDao: insert (вставка обработанного текста), findByPostId (выборка).
 *
 * БИБЛИОТЕКИ
 * Exposed ORM — DSL запросов (insert, selectAll).
 *
 * СВЯЗИ
 * Таблица ProcessedTexts (storage/tables). postId ссылается на post.
 */
package com.example.lomanalyzer.storage.dao

import com.example.lomanalyzer.storage.tables.ProcessedTexts
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction

/**
 * DAO обработанных текстов постов. Каждый метод — отдельная транзакция.
 *
 * @param db подключение к БД (Exposed Database).
 */
class ProcessedTextDao(private val db: Database) {
    /**
     * INSERT обработанного текста для поста.
     * @param postId id поста, к которому относится результат NLP.
     * @param lemmasJson леммы в JSON или NULL.
     * @param entitiesJson именованные сущности в JSON или NULL.
     * @param language определённый язык или NULL.
     * @param cleanText очищенный текст или NULL.
     */
    fun insert(
        postId: Int,
        lemmasJson: String? = null,
        entitiesJson: String? = null,
        language: String? = null,
        cleanText: String? = null,
    ) = transaction(db) {
        ProcessedTexts.insert {
            it[ProcessedTexts.postId] = postId
            it[ProcessedTexts.lemmasJson] = lemmasJson
            it[ProcessedTexts.entitiesJson] = entitiesJson
            it[ProcessedTexts.language] = language
            it[ProcessedTexts.cleanText] = cleanText
        }
    }

    /**
     * SELECT обработанного текста по id поста.
     * @return ResultRow или null, если препроцессинг ещё не выполнен.
     */
    fun findByPostId(postId: Int): ResultRow? = transaction(db) {
        ProcessedTexts.selectAll().where { ProcessedTexts.postId eq postId }.singleOrNull()
    }
}
