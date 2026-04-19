package com.example.lomanalyzer.tools.sensitivity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class CorpusPost(
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
    @SerialName("ground_truth") val groundTruth: CorpusGroundTruth? = null,
)

@Serializable
data class CorpusGroundTruth(
    @SerialName("is_topic_relevant") val isTopicRelevant: Boolean? = null,
    val sentiment: String? = null,
    @SerialName("originality_type") val originalityType: String? = null,
)

@Serializable
data class CorpusAuthor(
    val id: Int,
    @SerialName("followers_count") val followersCount: Int,
    @SerialName("is_closed") val isClosed: Boolean = false,
    @SerialName("discovery_source") val discoverySource: String = "SEED",
)

@Serializable
data class CorpusRoleGT(
    @SerialName("author_id") val authorId: Int,
    @SerialName("expected_role") val expectedRole: String,
)

@Serializable
data class CorpusAnomalyGT(
    val date: String,
    @SerialName("expected_types") val expectedTypes: List<String>,
)

@Serializable
data class TestCorpusData(
    val version: String,
    val posts: List<CorpusPost>,
    val authors: List<CorpusAuthor>,
    @SerialName("ground_truth_roles") val groundTruthRoles: List<CorpusRoleGT> = emptyList(),
    @SerialName("ground_truth_anomalies") val groundTruthAnomalies: List<CorpusAnomalyGT> = emptyList(),
)

object TestCorpusLoader {
    private val json = Json { ignoreUnknownKeys = true }

    fun load(jsonContent: String): TestCorpusData =
        json.decodeFromString(jsonContent)

    fun validate(corpus: TestCorpusData): List<String> {
        val errors = mutableListOf<String>()

        if (corpus.posts.isEmpty()) errors.add("No posts")
        if (corpus.authors.isEmpty()) errors.add("No authors")

        val authorIds = corpus.authors.map { it.id }.toSet()
        for (post in corpus.posts) {
            if (post.fromId !in authorIds) {
                errors.add("Post ${post.id} references unknown author ${post.fromId}")
            }
            if (post.groundTruth == null) {
                errors.add("Post ${post.id} missing ground_truth")
            }
        }

        for (role in corpus.groundTruthRoles) {
            if (role.authorId !in authorIds) {
                errors.add("Role GT references unknown author ${role.authorId}")
            }
        }

        return errors
    }
}
