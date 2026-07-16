/*
 * НАЗНАЧЕНИЕ
 * Построение реестра авторов и обогащение их профилями (этапы 3-4 алгоритма). Реестр =
 * все уникальные авторы (fromId) тематических постов окна CURRENT; профили дают число
 * подписчиков, screen_name и признак закрытого аккаунта.
 *
 * ЧТО ВНУТРИ
 * Класс AuthorProfileCollector с методом collectAndRegister: выявление уникальных авторов,
 * пакетный запрос профилей через users.get, запись/обновление авторов и связи сессия-автор.
 *
 * МЕТОД
 * Этап 3 — distinct по fromId. Этап 4 — батчи по 200 id (лимит users.get); для каждого
 * пользователя обновляется существующая запись или создаётся новая, затем линкуется к сессии.
 * Закрытые аккаунты логируются и исключаются из дальнейшей обработки.
 *
 * СВЯЗИ
 * VkApiClient.usersGet — профили; PostDao — источник fromId; AuthorDao/LinkDao — запись
 * авторов и связей; SessionEventService/ProgressReporter/Logger — журнал и прогресс.
 */
package com.example.lomanalyzer.vk

import com.example.lomanalyzer.observability.Logger
import com.example.lomanalyzer.orchestration.ProgressEvent
import com.example.lomanalyzer.orchestration.ProgressReporter
import com.example.lomanalyzer.storage.dao.AuthorDao
import com.example.lomanalyzer.storage.dao.LinkDao
import com.example.lomanalyzer.storage.dao.PostDao
import com.example.lomanalyzer.storage.tables.Authors
import com.example.lomanalyzer.storage.tables.Posts
import kotlinx.coroutines.delay

/**
 * Collects author profiles from VK API (diploma 2.1.1, stage 3-4).
 *
 * Stage 3: builds author registry = all unique authors of topic posts.
 * Stage 4: enriches each author with profile data (followers, screen_name, is_closed).
 *
 * Closed accounts are logged but not included in processing.
 * Conservative imputation: missing aggregated indicators use median of collected authors.
 */
class AuthorProfileCollector(
    private val apiClient: VkApiClient,
    private val authorDao: AuthorDao,
    private val postDao: PostDao,
    private val linkDao: LinkDao,
    private val sessionEventService: SessionEventService,
    private val progressReporter: ProgressReporter,
    private val logger: Logger,
) {
    companion object {
        /** Размер батча для users.get (VK позволяет до 1000, здесь консервативно 200). */
        private const val USERS_GET_BATCH = 200
    }

    /**
     * Build author registry from CURRENT posts and enrich with VK profiles.
     * Returns count of registered authors.
     */
    suspend fun collectAndRegister(sessionId: Int, accessToken: String): Int {
        // Этап 3: выявляем уникальных авторов (fromId) тематических постов окна CURRENT
        val posts = postDao.findBySessionAndWindow(sessionId, "CURRENT")
        val uniqueVkIds = posts.map { it[Posts.fromId] }.filter { it > 0 }.distinct()

        if (uniqueVkIds.isEmpty()) {
            logger.info("No authors found in CURRENT posts for session #$sessionId")
            return 0
        }

        sessionEventService.logInfo(sessionId, "Реестр авторов: ${uniqueVkIds.size} уникальных авторов")

        // Этап 4: получаем профили батчами по 200 (ограничение users.get)
        var registered = 0
        var closedCount = 0
        val batches = uniqueVkIds.chunked(USERS_GET_BATCH)

        for ((batchIdx, batch) in batches.withIndex()) {
            // Один запрос users.get на весь батч id
            val response = apiClient.usersGet(batch, accessToken)

            if (response.error != null) {
                // Ошибку батча журналируем и переходим к следующему
                sessionEventService.logApiError(
                    sessionId, "users.get", response.error.errorCode, response.error.errorMsg,
                )
                continue
            }

            val users = response.response ?: continue
            for (user in users) {
                // Ищем автора в БД: если есть — обновляем профиль, иначе создаём
                val existing = authorDao.findByVkId(user.id)
                val authorDbId = if (existing != null) {
                    // Обновляем существующую запись свежими данными профиля
                    authorDao.update(existing[Authors.id].value) {
                        this[Authors.firstName] = user.firstName
                        this[Authors.lastName] = user.lastName
                        this[Authors.screenName] = user.screenName
                        this[Authors.followersCount] = user.followersCount
                        this[Authors.isClosed] = user.isClosed
                    }
                    existing[Authors.id].value
                } else {
                    // Создаём новую запись автора; источник обнаружения — тематический пост
                    authorDao.insert(
                        vkId = user.id,
                        firstName = user.firstName,
                        lastName = user.lastName,
                        screenName = user.screenName,
                        followersCount = user.followersCount,
                        isClosed = user.isClosed,
                        discoverySource = "TOPIC_POST",
                    )
                }

                // Связываем автора с текущей сессией
                linkDao.linkSessionAuthor(sessionId, authorDbId)
                registered++

                // Закрытые аккаунты помечаем в журнале (исключаются из обработки)
                if (user.isClosed) {
                    closedCount++
                    sessionEventService.logClosedAccount(sessionId, user.id)
                }
            }

            // Обновляем прогресс после каждого батча
            val processed = ((batchIdx + 1) * USERS_GET_BATCH).coerceAtMost(uniqueVkIds.size)
            progressReporter.update(ProgressEvent(
                stage = "Профили авторов: $processed/${uniqueVkIds.size}",
                completedItems = processed,
                totalItems = uniqueVkIds.size,
            ))

            // Логируем вехи примерно каждые 20% обработанных батчей
            if (batches.size >= 5 && (batchIdx + 1) % (batches.size / 5).coerceAtLeast(1) == 0) {
                sessionEventService.logProgress(sessionId, "Профили авторов", batchIdx + 1, batches.size)
            }

            delay(350) // пауза между батчами (rate limit)
        }

        if (closedCount > 0) {
            sessionEventService.logInfo(
                sessionId,
                "Закрытые аккаунты: $closedCount из $registered (исключены из обработки)",
            )
        }

        sessionEventService.logInfo(
            sessionId,
            "Реестр авторов завершён: $registered авторов ($closedCount закрытых)",
        )

        return registered
    }
}
