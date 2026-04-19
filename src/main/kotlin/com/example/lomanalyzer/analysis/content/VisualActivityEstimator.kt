package com.example.lomanalyzer.analysis.content

/**
 * VAR_a = count(containsMedia) / total_posts_a per author.
 */
object VisualActivityEstimator {
    fun compute(mediaPostCount: Int, totalPostCount: Int): Float {
        if (totalPostCount == 0) return 0f
        return mediaPostCount.toFloat() / totalPostCount
    }
}
