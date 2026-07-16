/*
 * НАЗНАЧЕНИЕ
 * Единое место, где ответ VK (VkPost) превращается в строку таблицы post.
 * Раньше это отображение из 14 полей было выписано вручную в четырёх местах — в
 * двух коллекторах сообществ (ныне объединены в CommunityPostCollector), а также
 * в NewsfeedSearchCollector и AuthorWallCollector — и отличалось между ними
 * только окном сбора. Любая правка (новое поле VK, другое значение по умолчанию)
 * требовала синхронно изменить все четыре копии, а пропуск одной давал бы
 * расхождение данных между окнами, которое ничем не ловится.
 *
 * ЧТО ВНУТРИ
 * PostDao.insertVkPost — функция-расширение, выполняющая отображение и вставку.
 *
 * МЕТОД
 * Отображение объявлено расширением DAO и живёт в пакете vk/, а не в storage/:
 * так модуль хранения не узнаёт о моделях VK и остаётся независимым от
 * источника данных (см. docs/architecture.md — модули общаются только через БД).
 *
 * Импорт готового JSON (import/JsonDataImporter) сюда сознательно НЕ переведён:
 * он отображает ImportPost — другой тип с уже плоскими полями (likes: Int против
 * VkPost.likes?.count). Общей у них остаётся только целевая таблица, и сведение
 * их в одну функцию связало бы путь импорта с моделью VK без выигрыша.
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
