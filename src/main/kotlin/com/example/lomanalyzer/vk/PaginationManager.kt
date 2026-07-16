/*
 * НАЗНАЧЕНИЕ
 * Обход всех страниц стены сообщества/пользователя через wall.get с пагинацией по
 * offset (модуль сбора данных, этап 2). Используется коллекторами BaselineCollector
 * и CurrentCollector для выгрузки постов сообществ.
 *
 * ЧТО ВНУТРИ
 * Класс PaginationManager с методом fetchAllPosts: листает страницы по pageSize,
 * пока не достигнут лимит постов, конец ленты или граница по времени.
 *
 * СВЯЗИ
 * Запросы идут через VkApiClient.wallGet; обработка rate limit, flood control и
 * повторов выполняется внутри VkApiClient (rateLimiter + backoff).
 */
package com.example.lomanalyzer.vk

import com.example.lomanalyzer.vk.models.VkPost

/**
 * Менеджер постраничного обхода стены через wall.get.
 *
 * @param apiClient клиент VK API.
 * @param pageSize число постов на страницу (максимум VK для wall.get — 100).
 */
class PaginationManager(
    private val apiClient: VkApiClient,
    private val pageSize: Int = 100,
) {
    /**
     * Выгружает посты со стены [ownerId], листая страницы по offset.
     * @param maxPosts верхний предел числа собираемых постов.
     * @param sinceTimestamp нижняя граница по дате (Unix-секунды): посты старше неё прекращают сбор.
     * @return список собранных постов в порядке выдачи VK (от новых к старым).
     */
    @Suppress("LoopWithTooManyJumpStatements")
    suspend fun fetchAllPosts(
        ownerId: Int,
        accessToken: String,
        maxPosts: Int = Int.MAX_VALUE,
        sinceTimestamp: Long? = null,
    ): List<VkPost> {
        val allPosts = mutableListOf<VkPost>()
        var offset = 0 // текущее смещение пагинации

        while (allPosts.size < maxPosts) {
            // Ошибки VK API (rate limits, flood control) обрабатываются
            // внутри VkApiClient.request() автоматическим повтором (backoff)
            val response = apiClient.wallGet(ownerId, offset, pageSize, accessToken)
            val wall = response.response ?: break // нет данных — выходим
            if (wall.items.isEmpty()) break // пустая страница — конец ленты

            for (post in wall.items) {
                // Пропускаем «пустышки» (записи без id и владельца)
                if (post.id == 0 && post.ownerId == 0) continue
                // Достигли постов старше нижней границы по времени — дальше только старее, выходим
                if (sinceTimestamp != null && post.date < sinceTimestamp) {
                    return allPosts
                }
                allPosts.add(post)
                if (allPosts.size >= maxPosts) break // достигнут лимит постов
            }

            offset += pageSize // переход к следующей странице
            if (offset >= wall.count) break // вышли за общее число постов на стене
        }

        return allPosts
    }
}
