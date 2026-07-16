/*
 * НАЗНАЧЕНИЕ
 * Единое место, где ответ VK (VkPost) отображается в строку таблицы post.
 * Отображением пользуются все коллекторы пакета: CommunityPostCollector,
 * NewsfeedSearchCollector и AuthorWallCollector.
 *
 * ЧТО ВНУТРИ
 * PostDao.insertVkPost — функция-расширение, выполняющая отображение и вставку.
 *
 * МЕТОД
 * Отображение объявлено расширением DAO и живёт в пакете vk/, а не в storage/:
 * так модуль хранения не зависит от моделей VK и остаётся независимым от
 * источника данных (см. docs/architecture.md — модули общаются только через БД).
 *
 * Импорт готового JSON (import/JsonDataImporter) использует собственное
 * отображение: его исходный тип ImportPost имеет плоские поля (likes: Int против
 * VkPost.likes?.count). Общая у них только целевая таблица.
 *
 * СВЯЗИ
 * VkPost (vk/models/VkModels.kt) -> PostDao.insert (storage/dao/).
 */
package com.example.lomanalyzer.vk

import com.example.lomanalyzer.storage.dao.PostDao
import com.example.lomanalyzer.vk.models.VkPost

/**
 * Сохраняет пост VK в указанное окно сбора.
 *
 * Счётчики VK приходят вложенными объектами и могут отсутствовать — отсутствие
 * трактуется как 0 (для views — как «неизвестно», null: ноль просмотров и
 * отсутствие данных о просмотрах различаются при расчёте охвата Reach_a).
 *
 * @param sessionId сессия анализа, к которой относится пост.
 * @param post пост в том виде, в каком его вернул VK API.
 * @param window окно сбора: "BASELINE" (фон) либо "CURRENT" (тематический период).
 * @return id строки в таблице post (существующей, если пост уже сохранён).
 */
fun PostDao.insertVkPost(sessionId: Int, post: VkPost, window: String): Int =
    insert(
        sessionId = sessionId,
        vkId = post.id,
        ownerId = post.ownerId,
        fromId = post.fromId,
        publishedAt = post.date,
        text = post.text,
        window = window,
        ownTextLength = post.text.length,
        likes = post.likes?.count ?: 0,
        reposts = post.reposts?.count ?: 0,
        comments = post.comments?.count ?: 0,
        // null сохраняем как есть: «просмотров не было» и «VK не отдал просмотры» —
        // разные случаи, и Reach_a обрабатывает их по-разному.
        views = post.views?.count,
        containsMedia = !post.attachments.isNullOrEmpty(),
        hasCopyHistory = !post.copyHistory.isNullOrEmpty(),
    )
