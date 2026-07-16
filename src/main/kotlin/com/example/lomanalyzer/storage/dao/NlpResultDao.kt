/*
 * НАЗНАЧЕНИЕ
 * DAO (слой доступа к данным) для кэша результатов NLP (таблица nlp_results). Кэширует
 * по хэшу текста (textHash) результаты обработки: леммы (lemmasJson) и сентимент
 * (sentiment/score/method), чтобы не пересчитывать NLP для повторяющихся текстов.
 * NLP-модуль двухрежимный: Python sidecar (dostoevsky, pymorphy3, natasha,
 * rubert-tiny2) с fallback на Kotlin. Версия модели — modelVersion (по умолчанию v1).
 * Обмен между модулями — только через SQLite.
 *
 * ЧТО ВНУТРИ
 * Класс NlpResultDao: findByHash (поиск кэша по хэшу+версии),
 * insertLemmas (upsert лемм), insertSentiment (upsert сентимента). Естественный ключ
 * кэша — пара (textHash, modelVersion).
 *
 * БИБЛИОТЕКИ
 * Exposed ORM — DSL запросов. java.time.Instant — created_at в epoch millis.
 *
 * СВЯЗИ
 * Таблица NlpResults (storage/tables). Используется препроцессингом и сентиментом.
 */
package com.example.lomanalyzer.storage.dao

import com.example.lomanalyzer.storage.tables.NlpResults
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant

/**
 * DAO кэша NLP. Каждый метод — отдельная транзакция.
 *
 * @param db подключение к БД (Exposed Database).
 */
class NlpResultDao(private val db: Database) {

    /**
     * SELECT записи кэша по хэшу текста и версии модели.
     * @param textHash хэш исходного текста (ключ кэша).
     * @param modelVersion версия NLP-модели (по умолчанию v1).
     * @return ResultRow или null, если в кэше ничего нет.
     */
    fun findByHash(textHash: String, modelVersion: String = "v1"): ResultRow? = transaction(db) {
        NlpResults.selectAll().where {
            (NlpResults.textHash eq textHash) and (NlpResults.modelVersion eq modelVersion)
        }.singleOrNull()
    }

    /**
     * Upsert лемм в кэш по ключу (textHash, modelVersion).
     * @param lemmasJson сериализованные в JSON леммы текста.
     * @return id обновлённой или вновь созданной строки.
     */
    fun insertLemmas(textHash: String, lemmasJson: String, modelVersion: String = "v1"): Int = transaction(db) {
        // Проверяем кэш по хэшу+версии
        val existing = findByHash(textHash, modelVersion)
        if (existing != null) {
            // Запись есть — обновляем только поле лемм (остальные поля не трогаем)
            NlpResults.update({ NlpResults.id eq existing[NlpResults.id] }) {
                it[NlpResults.lemmasJson] = lemmasJson
            }
            return@transaction existing[NlpResults.id].value
        }
        // Записи нет — создаём новую с отметкой created_at
        NlpResults.insertAndGetId {
            it[NlpResults.textHash] = textHash
            it[NlpResults.modelVersion] = modelVersion
            it[NlpResults.lemmasJson] = lemmasJson
            it[NlpResults.createdAt] = Instant.now().toEpochMilli()
        }.value
    }

    /**
     * Upsert результата сентимента в кэш по ключу (textHash, modelVersion).
     * @param sentiment метка тональности (например, positive/neutral/negative).
     * @param score числовая оценка тональности.
     * @param method способ получения (sidecar-модель или Kotlin fallback).
     * @return id обновлённой или вновь созданной строки.
     */
    fun insertSentiment(
        textHash: String,
        sentiment: String,
        score: Float,
        method: String,
        modelVersion: String = "v1",
    ): Int = transaction(db) {
        // Проверяем кэш по хэшу+версии
        val existing = findByHash(textHash, modelVersion)
        if (existing != null) {
            // Запись есть — обновляем поля сентимента
            NlpResults.update({ NlpResults.id eq existing[NlpResults.id] }) {
                it[NlpResults.sentiment] = sentiment
                it[NlpResults.score] = score
                it[NlpResults.method] = method
            }
            return@transaction existing[NlpResults.id].value
        }
        // Записи нет — создаём новую с отметкой created_at
        NlpResults.insertAndGetId {
            it[NlpResults.textHash] = textHash
            it[NlpResults.modelVersion] = modelVersion
            it[NlpResults.sentiment] = sentiment
            it[NlpResults.score] = score
            it[NlpResults.method] = method
            it[NlpResults.createdAt] = Instant.now().toEpochMilli()
        }.value
    }
}
