package com.example.lomanalyzer.analysis.lom

import kotlin.math.ln

data class PostReachData(
    val postId: Int,
    val reposts: Int,
    val collectedReposterFollowers: List<Int>,
    val approximated: Boolean,
)

/**
 * M_reach_i = sum(F_r) for collected reposters.
 * For reposts > 200: M_reach_i ≈ reposts_i * F_tilde.
 * F_tilde = median followers across ALL collected reposters (if >= 30),
 * otherwise F_tilde_reference.
 *
 * After reposter finalization (stage 18), recompute all approximated posts
 * with the final session-wide F_tilde.
 */
class DisseminationReachComponent(
    private val fTildeReference: Int = 500,
) {
    companion object {
        private const val MIN_COLLECTED_FOR_SESSION_FTILDE = 30
    }

    fun computeFTilde(allCollectedFollowers: List<Int>): Int {
        if (allCollectedFollowers.size < MIN_COLLECTED_FOR_SESSION_FTILDE) {
            return fTildeReference
        }
        val sorted = allCollectedFollowers.sorted()
        return sorted[sorted.size / 2]
    }

    fun computeMReach(post: PostReachData, fTilde: Int): Long =
        if (post.approximated || post.collectedReposterFollowers.isEmpty()) {
            post.reposts.toLong() * fTilde
        } else {
            post.collectedReposterFollowers.sumOf { it.toLong() }
        }

    fun computeAuthorRaw(postReaches: List<Long>): Double {
        if (postReaches.isEmpty()) return 0.0
        return postReaches.map { ln(1.0 + it) }.average()
    }

    /**
     * Recompute M_reach for all approximated posts using final F_tilde.
     * Required by v6 after reposter collection finalization (stage 18).
     */
    fun recomputeApproximated(posts: List<PostReachData>, fTilde: Int): List<Pair<Int, Long>> =
        posts.filter { it.approximated }.map { post ->
            post.postId to (post.reposts.toLong() * fTilde)
        }
}
