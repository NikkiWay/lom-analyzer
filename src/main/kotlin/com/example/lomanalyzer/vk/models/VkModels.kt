/*
 * НАЗНАЧЕНИЕ
 * DTO-модели для разбора JSON-ответов VK API (модуль сбора данных, этапы 2-4).
 * Каждый класс отражает структуру ответа конкретного метода VK; именно в эти типы
 * VkApiClient десериализует тело ответа.
 *
 * ЧТО ВНУТРИ
 * Обёртка VkResponse (response или error), VkError; модели постов/комментариев
 * (VkPost, VkComment) и их счётчиков (VkLikes, VkReposts, VkComments, VkViews,
 * VkAttachment); пользователи и сообщества (VkUser, VkGroup); ответы методов
 * поиска и получения (wall, groups, users, newsfeed, comments, likes), резолва
 * имени и пакетного execute.
 *
 * ФРЕЙМВОРКИ
 * kotlinx.serialization: аннотация @Serializable делает классы разбираемыми из JSON,
 * @SerialName сопоставляет поля Kotlin со snake_case-ключами VK (например from_id).
 * Значения по умолчанию и nullable-поля защищают от отсутствующих ключей в ответе.
 */
package com.example.lomanalyzer.vk.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Универсальная обёртка ответа VK: заполнено либо [response] (успех), либо [error].
 * @param T тип полезной нагрузки конкретного метода.
 */
@Serializable
data class VkResponse<T>(
    val response: T? = null,
    val error: VkError? = null,
)

/**
 * Описание ошибки VK API: числовой код и текст сообщения.
 * Ключевые коды в проекте: 9 — flood control, 18 — аккаунт удалён/заблокирован, 30 — закрытый профиль.
 */
@Serializable
data class VkError(
    @SerialName("error_code") val errorCode: Int,
    @SerialName("error_msg") val errorMsg: String,
)

/** Ответ wall.get: общее число постов на стене и страница постов. */
@Serializable
data class VkWallResponse(
    val count: Int,
    val items: List<VkPost>,
)

/**
 * Пост VK. ownerId — владелец стены, fromId — фактический автор (источник реестра авторов).
 * copyHistory непустой — пост является репостом; счётчики лайков/репостов/комментариев/просмотров
 * и attachments используются для оценок и метаданных.
 */
@Serializable
data class VkPost(
    val id: Int = 0,
    @SerialName("owner_id") val ownerId: Int = 0,
    @SerialName("from_id") val fromId: Int = 0,
    val date: Long = 0,
    val text: String = "",
    val likes: VkLikes? = null,
    val reposts: VkReposts? = null,
    val comments: VkComments? = null,
    val views: VkViews? = null,
    val attachments: List<VkAttachment>? = null,
    @SerialName("copy_history") val copyHistory: List<VkPost>? = null,
)

/** Счётчик лайков объекта. */
@Serializable
data class VkLikes(val count: Int = 0)

/** Счётчик репостов поста. */
@Serializable
data class VkReposts(val count: Int = 0)

/** Счётчик комментариев поста. */
@Serializable
data class VkComments(val count: Int = 0)

/** Счётчик просмотров поста. */
@Serializable
data class VkViews(val count: Int = 0)

/** Вложение поста; в проекте важен лишь факт наличия медиа (по полю type). */
@Serializable
data class VkAttachment(val type: String)

/**
 * Профиль пользователя VK (ответ users.get). isClosed=true — закрытый профиль (исключается из обработки),
 * followersCount — число подписчиков для оценок структурного влияния.
 */
@Serializable
data class VkUser(
    val id: Int,
    @SerialName("first_name") val firstName: String = "",
    @SerialName("last_name") val lastName: String = "",
    @SerialName("screen_name") val screenName: String? = null,
    @SerialName("followers_count") val followersCount: Int? = null,
    @SerialName("is_closed") val isClosed: Boolean = false,
)

/**
 * Сообщество VK. У групп isClosed — целое (0/1/2), membersCount — размер аудитории.
 */
@Serializable
data class VkGroup(
    val id: Int,
    val name: String = "",
    @SerialName("screen_name") val screenName: String? = null,
    @SerialName("members_count") val membersCount: Int? = null,
    @SerialName("is_closed") val isClosed: Int = 0,
    val type: String? = null,
    val description: String? = null,
    val activity: String? = null,
)

/** Ответ groups.search: число найденных сообществ и страница результатов. */
@Serializable
data class VkGroupSearchResponse(
    val count: Int,
    val items: List<VkGroup>,
)

/** Ответ groups.getById: VK возвращает сообщества во вложенном поле groups. */
@Serializable
data class VkGroupsGetByIdResponse(
    val groups: List<VkGroup> = emptyList(),
)

/** Ответ users.search: число найденных пользователей и страница результатов. */
@Serializable
data class VkUserSearchResponse(
    val count: Int = 0,
    val items: List<VkUser> = emptyList(),
)

/**
 * Ответ newsfeed.search. nextFrom — непрозрачный курсор следующей страницы (pagination):
 * null означает, что страниц больше нет.
 */
@Serializable
data class VkNewsfeedSearchResponse(
    val count: Int? = null,
    val items: List<VkPost> = emptyList(),
    @SerialName("total_count") val totalCount: Int? = null,
    @SerialName("next_from") val nextFrom: String? = null,
)

/** Ответ wall.getComments: общее число комментариев под постом и страница комментариев. */
@Serializable
data class VkWallCommentsResponse(
    val count: Int,
    val items: List<VkComment>,
)

/**
 * Комментарий VK. fromId — автор комментария (нужен для оси отклика аудитории Resp_a),
 * ownerId/postId привязывают комментарий к посту.
 */
@Serializable
data class VkComment(
    val id: Int = 0,
    @SerialName("from_id") val fromId: Int = 0,
    val date: Long = 0,
    val text: String = "",
    val likes: VkLikes? = null,
    @SerialName("owner_id") val ownerId: Int = 0,
    @SerialName("post_id") val postId: Int = 0,
)

/** Ответ likes.getList: число и id пользователей (например, репостнувших пост). */
@Serializable
data class VkLikesListResponse(
    val count: Int,
    val items: List<Int>,
)

/** Ответ utils.resolveScreenName: тип объекта (user/group/...) и его числовой id. */
@Serializable
data class VkResolvedScreenName(
    val type: String? = null,
    @SerialName("object_id") val objectId: Int? = null,
)

/**
 * Ответ метода execute. response — массив результатов вложенных вызовов в виде сырых
 * JsonElement (каждый разбирается отдельно, так как методы могут возвращать разные типы).
 */
@Serializable
data class VkExecuteResponse(
    val response: List<kotlinx.serialization.json.JsonElement>? = null,
    val error: VkError? = null,
)
