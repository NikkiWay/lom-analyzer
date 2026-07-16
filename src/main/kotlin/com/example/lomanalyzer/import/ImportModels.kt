/*
 * НАЗНАЧЕНИЕ
 * DTO-модели для импорта данных из внешнего JSON-файла (альтернатива сбору
 * напрямую через VK API). Описывают структуру входного датасета: сообщества,
 * авторы, посты и комментарии. Относится к модулю импорта (см. JsonDataImporter).
 *
 * ЧТО ВНУТРИ
 * data class ImportDataset — корневой объект файла; ImportCommunity, ImportAuthor,
 * ImportPost, ImportComment — отдельные сущности. Все классы помечены
 * @Serializable для разбора kotlinx.serialization.
 *
 * БИБЛИОТЕКИ
 * kotlinx.serialization (@Serializable, @SerialName) — декларативный разбор JSON;
 * @SerialName сопоставляет имена полей JSON с именами свойств Kotlin.
 *
 * СВЯЗИ
 * JsonDataImporter разбирает JSON в ImportDataset и переносит данные в БД через DAO.
 */
package com.example.lomanalyzer.import

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Корневой объект импортируемого датасета.
 *
 * @property communities список сообществ.
 * @property authors список авторов.
 * @property posts список постов.
 * @property comments список комментариев (по умолчанию пуст).
 */
@Serializable
data class ImportDataset(
    val communities: List<ImportCommunity>,
    val authors: List<ImportAuthor>,
    val posts: List<ImportPost>,
    val comments: List<ImportComment> = emptyList(),
)

/** Импортируемое сообщество VK (id, название, screen name, число участников, закрытость, тип). */
@Serializable
data class ImportCommunity(
    val vkId: Int,
    val name: String,
    val screenName: String? = null,
    val membersCount: Int? = null,
    val isClosed: Boolean = false,
    val type: String? = null,
)

/** Импортируемый автор (пользователь) VK (id, имя/фамилия, screen name, число подписчиков, закрытость). */
@Serializable
data class ImportAuthor(
    val vkId: Int,
    val firstName: String? = null,
    val lastName: String? = null,
    val screenName: String? = null,
    val followersCount: Int? = null,
    val isClosed: Boolean = false,
)

/**
 * Импортируемый пост.
 *
 * @property vkId id поста в VK.
 * @property ownerId id владельца стены (сообщество/пользователь, у кого опубликован пост).
 * @property fromId id автора публикации.
 * @property date время публикации (Unix-время, секунды).
 * @property text текст поста.
 * @property likes,reposts,comments,views метрики вовлечённости.
 * @property containsMedia содержит ли пост медиа-вложения.
 * @property hasCopyHistory является ли пост репостом (есть copy_history).
 * @property window окно сбора (например, CURRENT — текущее, или фоновое).
 */
@Serializable
data class ImportPost(
    val vkId: Int,
    @SerialName("ownerId") val ownerId: Int,
    @SerialName("fromId") val fromId: Int,
    val date: Long,
    val text: String = "",
    val likes: Int = 0,
    val reposts: Int = 0,
    val comments: Int = 0,
    val views: Int? = null,
    val containsMedia: Boolean = false,
    val hasCopyHistory: Boolean = false,
    val window: String = "CURRENT",
)

/**
 * Импортируемый комментарий.
 *
 * @property vkId id комментария в VK.
 * @property postVkId id поста, к которому относится комментарий.
 * @property postOwnerId id владельца стены поста (нужен для однозначного поиска поста).
 * @property fromId id автора комментария.
 * @property date время публикации (Unix-время, секунды).
 * @property text текст комментария.
 * @property likes число лайков комментария.
 */
@Serializable
data class ImportComment(
    val vkId: Int,
    @SerialName("postVkId") val postVkId: Int,
    @SerialName("postOwnerId") val postOwnerId: Int,
    @SerialName("fromId") val fromId: Int,
    val date: Long,
    val text: String = "",
    val likes: Int = 0,
)
