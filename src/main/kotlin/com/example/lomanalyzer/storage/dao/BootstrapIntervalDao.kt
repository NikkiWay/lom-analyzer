/*
 * НАЗНАЧЕНИЕ
 * DAO (слой доступа к данным) для доверительных интервалов бутстрапа (таблица
 * bootstrap_intervals). Хранит результаты этапа 6 алгоритма — границы CI для оценок
 * автора, полученные бутстрапом (одноуровневый B=1000; двухуровневый 300×100 только
 * для Resp_a). По архитектуре аналитическое ядро пишет сюда, а UI/экспорт читают —
 * обмен идёт исключительно через SQLite.
 *
 * ЧТО ВНУТРИ
 * Класс BootstrapIntervalDao: upsert (вставка-или-обновление интервала по ключу
 * сессия+автор+имя оценки), findBySession и findBySessionAndAuthor (выборки).
 *
 * МЕТОД
 * Хранит нижнюю/верхнюю границы CI (ciLo, ciHi), тип процедуры бутстрапа
 * (procedureType) и число итераций (iterations) для конкретной оценки scoreName.
 *
 * БИБЛИОТЕКИ
 * Exposed ORM — DSL запросов. java.time.Instant — created_at в epoch millis.
 *
 * СВЯЗИ
 * Таблица BootstrapIntervals (storage/tables). Оценки — см. analysis/inference.
 */
package com.example.lomanalyzer.storage.dao

import com.example.lomanalyzer.storage.tables.BootstrapIntervals
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant

/**
 * DAO доверительных интервалов бутстрапа. Каждый метод — отдельная транзакция.
 *
 * @param db подключение к БД (Exposed Database).
 */
class BootstrapIntervalDao(private val db: Database) {

    /**
     * Upsert (вставка-или-обновление) доверительного интервала.
     * Естественный ключ — тройка (sessionId, authorId, scoreName): для одной оценки
     * автора в рамках сессии хранится ровно один интервал.
     * @param scoreName имя оценки (например, Resp_a, Reach и т. п.).
     * @param ciLo нижняя граница доверительного интервала.
     * @param ciHi верхняя граница доверительного интервала.
     * @param procedureType тип процедуры бутстрапа (одно- или двухуровневый).
     * @param iterations число итераций бутстрапа.
     */
    fun upsert(
        sessionId: Int,
        authorId: Int,
        scoreName: String,
        ciLo: Float,
        ciHi: Float,
        procedureType: String,
        iterations: Int,
    ) = transaction(db) {
        // Ищем существующий интервал по естественному ключу сессия+автор+оценка
        val existing = BootstrapIntervals.selectAll().where {
            (BootstrapIntervals.sessionId eq sessionId) and
                (BootstrapIntervals.authorId eq authorId) and
                (BootstrapIntervals.scoreName eq scoreName)
        }.singleOrNull()

        if (existing != null) {
            // UPDATE: запись уже есть — обновляем границы и параметры процедуры
            BootstrapIntervals.update({
                (BootstrapIntervals.sessionId eq sessionId) and
                    (BootstrapIntervals.authorId eq authorId) and
                    (BootstrapIntervals.scoreName eq scoreName)
            }) {
                it[BootstrapIntervals.ciLo] = ciLo
                it[BootstrapIntervals.ciHi] = ciHi
                it[BootstrapIntervals.procedureType] = procedureType
                it[BootstrapIntervals.iterations] = iterations
            }
        } else {
            // INSERT: записи нет — создаём новую с отметкой времени created_at
            BootstrapIntervals.insert {
                it[BootstrapIntervals.sessionId] = sessionId
                it[BootstrapIntervals.authorId] = authorId
                it[BootstrapIntervals.scoreName] = scoreName
                it[BootstrapIntervals.ciLo] = ciLo
                it[BootstrapIntervals.ciHi] = ciHi
                it[BootstrapIntervals.procedureType] = procedureType
                it[BootstrapIntervals.iterations] = iterations
                it[BootstrapIntervals.createdAt] = Instant.now().toEpochMilli()
            }
        }
    }

    /**
     * SELECT всех интервалов сессии (по всем авторам и оценкам).
     * @return список ResultRow.
     */
    fun findBySession(sessionId: Int): List<ResultRow> = transaction(db) {
        BootstrapIntervals.selectAll()
            .where { BootstrapIntervals.sessionId eq sessionId }
            .toList()
    }

    /**
     * SELECT всех интервалов одного автора в рамках сессии (по всем его оценкам).
     * @return список ResultRow.
     */
    fun findBySessionAndAuthor(sessionId: Int, authorId: Int): List<ResultRow> = transaction(db) {
        BootstrapIntervals.selectAll().where {
            (BootstrapIntervals.sessionId eq sessionId) and
                (BootstrapIntervals.authorId eq authorId)
        }.toList()
    }
}
