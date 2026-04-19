package com.example.lomanalyzer.integration

import com.example.lomanalyzer.storage.dao.AuthorDao
import com.example.lomanalyzer.storage.dao.PostDao
import com.example.lomanalyzer.storage.dao.SessionDao
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database

@Serializable
data class TestPost(
    val id: Int,
    @SerialName("from_id") val fromId: Int,
    @SerialName("published_at") val publishedAt: Long,
    @SerialName("text_clean") val textClean: String,
    val likes: Int,
    val reposts: Int,
    val comments: Int,
    @SerialName("own_text_length") val ownTextLength: Int,
    @SerialName("has_copy_history") val hasCopyHistory: Boolean,
    @SerialName("contains_media") val containsMedia: Boolean,
)

@Serializable
data class TestAuthor(
    val id: Int,
    @SerialName("vk_id_hashed") val vkIdHashed: String,
    @SerialName("followers_count") val followersCount: Int,
    @SerialName("is_closed") val isClosed: Boolean,
    @SerialName("discovery_source") val discoverySource: String,
)

@Serializable
data class TestCorpus(
    val version: String,
    val posts: List<TestPost>,
    val authors: List<TestAuthor>,
)

class TestCorpusImporter(private val db: Database) {
    private val json = Json { ignoreUnknownKeys = true }

    fun import(corpusJson: String): Int {
        val corpus = json.decodeFromString<TestCorpus>(corpusJson)
        val sessionDao = SessionDao(db)
        val authorDao = AuthorDao(db)
        val postDao = PostDao(db)

        val sessionId = sessionDao.insert(
            name = "Test Corpus Session",
            topicQuery = "ecology",
            region = "synthetic",
        )

        for (author in corpus.authors) {
            authorDao.insert(
                vkId = author.id,
                followersCount = author.followersCount,
                isClosed = author.isClosed,
                discoverySource = author.discoverySource,
            )
        }

        for (post in corpus.posts) {
            postDao.insert(
                sessionId = sessionId,
                vkId = post.id,
                ownerId = -1,
                fromId = post.fromId,
                publishedAt = post.publishedAt * 1000,
                text = post.textClean,
                window = "BASELINE",
                ownTextLength = post.ownTextLength,
            )
        }

        return sessionId
    }
}
