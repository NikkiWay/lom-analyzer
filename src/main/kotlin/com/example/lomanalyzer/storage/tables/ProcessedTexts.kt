package com.example.lomanalyzer.storage.tables

import org.jetbrains.exposed.sql.Table

object ProcessedTexts : Table("processed_text") {
    val postId = reference("post_id", Posts).uniqueIndex()
    val lemmasJson = text("lemmas_json").nullable()
    val entitiesJson = text("entities_json").nullable()
    val language = text("language").nullable()
    val cleanText = text("clean_text").nullable()

    override val primaryKey = PrimaryKey(postId)
}
