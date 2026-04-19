package com.example.lomanalyzer.vk.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class VkResponse<T>(
    val response: T? = null,
    val error: VkError? = null,
)

@Serializable
data class VkError(
    @SerialName("error_code") val errorCode: Int,
    @SerialName("error_msg") val errorMsg: String,
)

@Serializable
data class VkWallResponse(
    val count: Int,
    val items: List<VkPost>,
)

@Serializable
data class VkPost(
    val id: Int,
    @SerialName("owner_id") val ownerId: Int,
    @SerialName("from_id") val fromId: Int,
    val date: Long,
    val text: String = "",
    val likes: VkLikes? = null,
    val reposts: VkReposts? = null,
    val comments: VkComments? = null,
    val views: VkViews? = null,
    val attachments: List<VkAttachment>? = null,
    @SerialName("copy_history") val copyHistory: List<VkPost>? = null,
)

@Serializable
data class VkLikes(val count: Int = 0)

@Serializable
data class VkReposts(val count: Int = 0)

@Serializable
data class VkComments(val count: Int = 0)

@Serializable
data class VkViews(val count: Int = 0)

@Serializable
data class VkAttachment(val type: String)

@Serializable
data class VkUser(
    val id: Int,
    @SerialName("first_name") val firstName: String = "",
    @SerialName("last_name") val lastName: String = "",
    @SerialName("screen_name") val screenName: String? = null,
    @SerialName("followers_count") val followersCount: Int? = null,
    @SerialName("is_closed") val isClosed: Boolean = false,
)

@Serializable
data class VkGroup(
    val id: Int,
    val name: String = "",
    @SerialName("screen_name") val screenName: String? = null,
    @SerialName("members_count") val membersCount: Int? = null,
    @SerialName("is_closed") val isClosed: Int = 0,
    val type: String? = null,
)

@Serializable
data class VkLikesListResponse(
    val count: Int,
    val items: List<Int>,
)

@Serializable
data class VkExecuteResponse(
    val response: List<kotlinx.serialization.json.JsonElement>? = null,
    val error: VkError? = null,
)
