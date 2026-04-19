package com.example.lomanalyzer.storage.dao

import com.example.lomanalyzer.storage.tables.ProcessedTexts
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction

class ProcessedTextDao(private val db: Database) {
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

    fun findByPostId(postId: Int): ResultRow? = transaction(db) {
        ProcessedTexts.selectAll().where { ProcessedTexts.postId eq postId }.singleOrNull()
    }
}
