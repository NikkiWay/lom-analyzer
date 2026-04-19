package com.example.lomanalyzer.storage.tables

import org.jetbrains.exposed.sql.Table

object SentimentResults : Table("sentiment_result") {
    val postId = reference("post_id", Posts).uniqueIndex()
    val sentiment = text("sentiment")
    val score = float("score").nullable()
    val method = text("method")
    val negationApplied = bool("negation_applied").default(false)
    val bootstrapAgreement = float("bootstrap_agreement").nullable()
    val bootstrapVariants = text("bootstrap_variants").nullable()

    override val primaryKey = PrimaryKey(postId)
}
