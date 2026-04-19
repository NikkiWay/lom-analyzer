package com.example.lomanalyzer.analysis.lom

import kotlin.math.ln
import kotlin.math.max

data class PostReactions(
    val likes: Int,
    val reposts: Int,
    val comments: Int,
)

/**
 * E_raw = ln(1 + r_bar) - gamma * ln(1 + F)
 * where r_bar = mean weighted reaction over all baseline posts after dedup collapsing.
 * weightedReaction = likes + 2*reposts + 1.5*comments
 */
object EngagementDensityComponent {
    fun weightedReaction(post: PostReactions): Double =
        post.likes + 2.0 * post.reposts + 1.5 * post.comments

    fun meanReaction(posts: List<PostReactions>): Double {
        if (posts.isEmpty()) return 0.0
        val total = posts.sumOf { weightedReaction(it) }
        return total / max(posts.size, 1)
    }

    fun computeRaw(rBar: Double, followers: Int, gamma: Double): Double =
        ln(1.0 + rBar) - gamma * ln(1.0 + followers)
}
