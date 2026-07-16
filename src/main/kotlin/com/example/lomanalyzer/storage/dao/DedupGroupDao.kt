/*
 * НАЗНАЧЕНИЕ
 * DAO (слой доступа к данным) для групп дубликатов (таблица dedup_groups) — результат
 * этапа дедупликации (этап 5 алгоритма, подраздел 2.1.1). Каждая запись связывает
 * пост-дубликат (duplicatePostId) с его каноническим оригиналом (canonicalPostId)
 * и хранит меру сходства и метод сравнения. Дедуп по NormalizedLevenshtein, порог
 * 0.90 (см. analysis/dedup). Обмен между модулями — только через SQLite.
 *
 * ЧТО ВНУТРИ
 * Класс DedupGroupDao: insert (идемпотентная запись пары канон/дубликат),
 * findBySession (все пары дубликатов сессии).
 *
 * БИБЛИОТЕКИ
 * Exposed ORM — DSL запросов (insertAndGetId, selectAll).
 *
 * СВЯЗИ
 * Таблица DedupGroups (storage/tables). canonical/duplicate ссылаются на post.
 */
package com.example.lomanalyzer.storage.dao

import com.example.lomanalyzer.storage.tables.DedupGroups
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction

/**
 * DAO групп дубликатов. Каждый метод — отдельная транзакция.
 *
 * @param db подключение к БД (Exposed Database).
 */
class DedupGroupDao(private val db: Database) {
    /**
     * Идемпотентный INSERT пары «канонический пост — дубликат». Пост может быть
     * дубликатом лишь один раз за сессию (UNIQUE-ограничение по duplicatePostId).
     * @param canonicalPostId id канонического (оригинального) поста.
     * @param duplicatePostId id поста-дубликата.
     * @param similarity мера сходства [0..1] (например, нормализованный Левенштейн).
     * @param method имя метода сравнения (например, NORMALIZED_LEVENSHTEIN).
     * @return id существующей или вновь созданной строки.
     */
    fun insert(
        sessionId: Int,
        canonicalPostId: Int,
        duplicatePostId: Int,
        similarity: Float,
        method: String,
    ): Int = transaction(db) {
        // Пост может быть дубликатом лишь один раз за сессию (UNIQUE-ограничение).
        // Если уже записан как дубликат другого канона — не дублируем запись.
        val existing = DedupGroups.selectAll().where {
            (DedupGroups.sessionId eq sessionId) and
                (DedupGroups.duplicatePostId eq duplicatePostId)
        }.singleOrNull()
        // Уже есть — возвращаем id существующей строки
        if (existing != null) return@transaction existing[DedupGroups.id].value

        DedupGroups.insertAndGetId {
            it[DedupGroups.sessionId] = sessionId
            it[DedupGroups.canonicalPostId] = canonicalPostId
            it[DedupGroups.duplicatePostId] = duplicatePostId
            it[DedupGroups.similarity] = similarity
            it[DedupGroups.method] = method
        }.value
    }

    /**
     * SELECT всех пар дубликатов сессии.
     * @return список ResultRow.
     */
    fun findBySession(sessionId: Int): List<ResultRow> = transaction(db) {
        DedupGroups.selectAll().where { DedupGroups.sessionId eq sessionId }.toList()
    }
}
