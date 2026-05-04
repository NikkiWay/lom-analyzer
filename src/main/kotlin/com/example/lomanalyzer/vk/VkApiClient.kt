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

    suspend fun groupsSearch(
        query: String,
        count: Int = 20,
        offset: Int = 0,
        type: String? = null,
        cityId: Int? = null,
        countryId: Int? = null,
        sort: Int = 6,
        accessToken: String,
    ): VkResponse<VkGroupSearchResponse> = request("groups.search") {
        parameter("q", query)
        parameter("count", count)
        if (offset > 0) parameter("offset", offset)
        if (type != null) parameter("type", type)
        if (cityId != null) parameter("city_id", cityId)
        if (countryId != null) parameter("country_id", countryId)
        parameter("sort", sort)
        parameter("fields", "members_count,screen_name,is_closed,description,activity")
        parameter("access_token", accessToken)
    }

    suspend fun usersSearch(
        query: String,
        count: Int = 20,
        cityId: Int? = null,
        countryId: Int? = null,
        sort: Int = 0,
        accessToken: String,
    ): VkResponse<VkUserSearchResponse> = request("users.search") {
        parameter("q", query)
        parameter("count", count)
        if (cityId != null) parameter("city", cityId)
        if (countryId != null) parameter("country", countryId)
        parameter("sort", sort)
        parameter("fields", "followers_count,screen_name,is_closed,city")
        parameter("access_token", accessToken)
    }

    suspend fun newsfeedSearch(
        query: String,
        count: Int = 200,
        startFrom: String? = null,
        accessToken: String,
    ): VkResponse<VkNewsfeedSearchResponse> = request("newsfeed.search") {
        parameter("q", query)
        parameter("count", count)
        if (startFrom != null) parameter("start_from", startFrom)
        parameter("access_token", accessToken)
    }

    suspend fun groupsGetById(
        groupIds: List<Int>,
        accessToken: String,
    ): VkResponse<List<VkGroup>> = groupsGetByStringIds(
        groupIds.joinToString(","), accessToken,
    )

    suspend fun groupsGetByStringIds(
        groupIds: String,
        accessToken: String,
    ): VkResponse<List<VkGroup>> {
        val raw: VkResponse<VkGroupsGetByIdResponse> = request("groups.getById") {
            parameter("group_ids", groupIds)
            parameter("fields", "members_count,screen_name,is_closed,description,activity")
            parameter("access_token", accessToken)
        }
        return VkResponse(
            response = raw.response?.groups,
            error = raw.error,
        )
    }

    suspend fun resolveScreenName(
        screenName: String,
        accessToken: String,
    ): VkResponse<VkResolvedScreenName> = request("utils.resolveScreenName") {
        parameter("screen_name", screenName)
        parameter("access_token", accessToken)
    }

    suspend fun usersGetByScreenNames(
        userIds: String,
        accessToken: String,
    ): VkResponse<List<VkUser>> = request("users.get") {
        parameter("user_ids", userIds)
        parameter("fields", "followers_count,screen_name,is_closed")
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
