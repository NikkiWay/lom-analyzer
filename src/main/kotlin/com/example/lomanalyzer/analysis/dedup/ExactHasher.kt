package com.example.lomanalyzer.analysis.dedup

import java.security.MessageDigest

/**
 * Stage 1: SHA-256 exact-match deduplication.
 * Applied to ALL posts with ownTextLength >= 30 for global DETECTED_COPY detection.
 * First by publishedAt becomes GROUP_LEADER; others become EXACT_COPY.
 */
object ExactHasher {
    private const val MIN_TEXT_LENGTH = 30

    private val URL_REGEX = Regex(Regex.escape("[url]"))
    private val USER_REGEX = Regex(Regex.escape("[user]"))
    private val HASHTAG_REGEX = Regex("#[\\wА-яёЁ]+")
    private val MULTI_SPACE = Regex("\\s+")

    fun isEligible(ownTextLength: Int): Boolean =
        ownTextLength >= MIN_TEXT_LENGTH

    fun normalizeAndHash(text: String): String {
        val normalized = text
            .lowercase()
            .let { URL_REGEX.replace(it, "") }
            .let { USER_REGEX.replace(it, "") }
            .let { HASHTAG_REGEX.replace(it, "") }
            .let { MULTI_SPACE.replace(it, " ") }
            .trim()

        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(normalized.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Groups posts by hash. Returns map of hash → list of (postId, publishedAt),
     * sorted by publishedAt ascending. First entry is the GROUP_LEADER.
     */
    fun findDuplicateGroups(
        posts: List<HashablePost>,
    ): Map<String, List<HashablePost>> {
        val hashMap = mutableMapOf<String, MutableList<HashablePost>>()

        for (post in posts) {
            if (!isEligible(post.ownTextLength)) continue
            val hash = normalizeAndHash(post.cleanText)
            hashMap.getOrPut(hash) { mutableListOf() }.add(post)
        }

        // Only keep groups with duplicates (size > 1), sort by publishedAt
        return hashMap
            .filter { it.value.size > 1 }
            .mapValues { (_, group) -> group.sortedBy { it.publishedAt } }
    }
}

data class HashablePost(
    val postId: Int,
    val fromId: Int,
    val publishedAt: Long,
    val cleanText: String,
    val ownTextLength: Int,
    val isTopicRelevant: Boolean?,
)
