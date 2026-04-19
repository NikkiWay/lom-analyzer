package com.example.lomanalyzer.storage.dao

import com.example.lomanalyzer.storage.tables.SentimentResults
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction

class SentimentResultDao(private val db: Database) {
    fun insert(
        postId: Int,
        sentiment: String,
        score: Float? = null,
        method: String,
        negationApplied: Boolean = false,
        bootstrapAgreement: Float? = null,
        bootstrapVariants: String? = null,
    ) = transaction(db) {
        SentimentResults.insert {
            it[SentimentResults.postId] = postId
            it[SentimentResults.sentiment] = sentiment
            it[SentimentResults.score] = score
            it[SentimentResults.method] = method
            it[SentimentResults.negationApplied] = negationApplied
            it[SentimentResults.bootstrapAgreement] = bootstrapAgreement
            it[SentimentResults.bootstrapVariants] = bootstrapVariants
        }
    }

    fun findByPostId(postId: Int): ResultRow? = transaction(db) {
        SentimentResults.selectAll().where { SentimentResults.postId eq postId }.singleOrNull()
    }
}
