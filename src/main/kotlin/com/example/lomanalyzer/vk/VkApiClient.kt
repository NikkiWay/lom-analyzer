package com.example.lomanalyzer.vk

import com.example.lomanalyzer.vk.models.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*

class VkApiClient(
    private val httpClient: HttpClient,
    private val rateLimiter: VkRateLimiter,
    private val backoff: VkBackoff,
    private val apiVersion: String = "5.199",
) {
    companion object {
        private const val BASE_URL = "https://api.vk.com/method"
    }

    suspend fun wallGet(
        ownerId: Int,
        offset: Int,
        count: Int,
        accessToken: String,
    ): VkResponse<VkWallResponse> = request("wall.get") {
        parameter("owner_id", ownerId)
        parameter("offset", offset)
        parameter("count", count)
        parameter("access_token", accessToken)
    }

    suspend fun usersGet(
        userIds: List<Int>,
        accessToken: String,
    ): VkResponse<List<VkUser>> = request("users.get") {
        parameter("user_ids", userIds.joinToString(","))
        parameter("fields", "followers_count,screen_name,is_closed")
        parameter("access_token", accessToken)
    }

    suspend fun groupsGetById(
        groupIds: List<Int>,
        accessToken: String,
    ): VkResponse<List<VkGroup>> = request("groups.getById") {
        parameter("group_ids", groupIds.joinToString(","))
        parameter("fields", "members_count,screen_name,is_closed")
        parameter("access_token", accessToken)
    }

    suspend fun likesGetList(
        ownerId: Int,
        itemId: Int,
        accessToken: String,
    ): VkResponse<VkLikesListResponse> = request("likes.getList") {
        parameter("type", "post")
        parameter("owner_id", ownerId)
        parameter("item_id", itemId)
        parameter("filter", "copies")
        parameter("access_token", accessToken)
    }

    suspend fun execute(
        code: String,
        accessToken: String,
    ): VkExecuteResponse {
        rateLimiter.acquire()
        return backoff.withRetry {
            val response: HttpResponse = httpClient.get("$BASE_URL/execute") {
                parameter("code", code)
                parameter("access_token", accessToken)
                parameter("v", apiVersion)
            }
            response.body()
        }
    }

    private suspend inline fun <reified T> request(
        method: String,
        crossinline params: HttpRequestBuilder.() -> Unit,
    ): T {
        rateLimiter.acquire()
        return backoff.withRetry {
            val response: HttpResponse = httpClient.get("$BASE_URL/$method") {
                parameter("v", apiVersion)
                params()
            }
            response.body()
        }
    }
}
