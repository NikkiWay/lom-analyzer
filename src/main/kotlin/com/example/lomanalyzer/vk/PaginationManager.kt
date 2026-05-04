package com.example.lomanalyzer.vk

import com.example.lomanalyzer.vk.models.VkPost

class PaginationManager(
    private val apiClient: VkApiClient,
    private val pageSize: Int = 100,
) {
    @Suppress("LoopWithTooManyJumpStatements")
    suspend fun fetchAllPosts(
        ownerId: Int,
        accessToken: String,
        maxPosts: Int = Int.MAX_VALUE,
        sinceTimestamp: Long? = null,
    ): List<VkPost> {
        val allPosts = mutableListOf<VkPost>()
        var offset = 0

        while (allPosts.size < maxPosts) {
            // VK API errors (rate limits, flood control) are handled
            // inside VkApiClient.request() with automatic backoff retry
            val response = apiClient.wallGet(ownerId, offset, pageSize, accessToken)
            val wall = response.response ?: break
            if (wall.items.isEmpty()) break

            for (post in wall.items) {
                if (post.id == 0 && post.ownerId == 0) continue
                if (sinceTimestamp != null && post.date < sinceTimestamp) {
                    return allPosts
                }
                allPosts.add(post)
                if (allPosts.size >= maxPosts) break
            }

            offset += pageSize
            if (offset >= wall.count) break
        }

        return allPosts
    }
}
