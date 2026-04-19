package com.example.lomanalyzer.analysis.lom

import kotlin.math.min

/**
 * Leave-one-out prior: p_a = sum_{a'!=a} N_topic_a' / sum_{a'!=a} N_all_a'
 * T_raw_a = (N_topic_a + alpha * p_a) / (N_all_a + alpha), capped at 1.0.
 * alpha = 5 (smoothing constant).
 */
object TopicFocusComponent {
    private const val ALPHA = 5.0

    fun computeLeaveOneOutPrior(
        totalTopicAll: Int,
        totalPostsAll: Int,
        authorTopicCount: Int,
        authorPostCount: Int,
    ): Double {
        val otherTopic = totalTopicAll - authorTopicCount
        val otherPosts = totalPostsAll - authorPostCount
        return if (otherPosts > 0) otherTopic.toDouble() / otherPosts else 0.5
    }

    fun computeRaw(topicCount: Int, allCount: Int, prior: Double): Double {
        val raw = (topicCount + ALPHA * prior) / (allCount + ALPHA)
        return min(raw, 1.0)
    }
}
