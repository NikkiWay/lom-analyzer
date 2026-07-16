/*
 * НАЗНАЧЕНИЕ
 * Импорт данных пайплайна из готового JSON-файла вместо сбора через VK API.
 * Загружает сообщества, авторов, посты и комментарии в БД и привязывает их к
 * сессии. Альтернативный источник данных для этапа сбора (см. docs/algorithm.md,
 * этап сбора данных) — удобно для воспроизводимости и офлайн-прогона.
 *
 * ЧТО ВНУТРИ
 * class JsonDataImporter: метод import(sessionId, jsonPath) и приватные шаги
 * importCommunities / importAuthors / importPosts / importComments. Вложенный
 * data class ImportResult — сводка по числу импортированных сущностей.
 *
 * ЛОГИКА
 * Сущности дедуплицируются по vkId (если уже есть в БД — переиспользуется её id,
 * иначе вставляется новая). Сообщества и авторы связываются с сессией через
 * LinkDao. Комментарии привязываются к посту поиском по паре vkId + ownerId
 * в рамках сессии. Прогресс публикуется через ProgressReporter, события — в Logger.
 *
 * БИБЛИОТЕКИ
 * kotlinx.serialization (Json) — разбор JSON в ImportDataset; DAO-слой (Exposed)
 * — запись в БД; ProgressReporter/Logger — прогресс и события.
 *
 * СВЯЗИ
 * Модели — ImportModels (ImportDataset и др.); DAO — CommunityDao, AuthorDao,
 * PostDao, CommentDao, LinkDao; оркестрация — ProgressReporter/ProgressEvent.
 */
package com.example.lomanalyzer.import

import com.example.lomanalyzer.observability.AppEvent
import com.example.lomanalyzer.observability.Logger
import com.example.lomanalyzer.orchestration.ProgressEvent
import com.example.lomanalyzer.orchestration.ProgressReporter
import com.example.lomanalyzer.storage.dao.AuthorDao
import com.example.lomanalyzer.storage.dao.CommentDao
import com.example.lomanalyzer.storage.dao.CommunityDao
import com.example.lomanalyzer.storage.dao.LinkDao
import com.example.lomanalyzer.storage.dao.PostDao
import com.example.lomanalyzer.storage.tables.Posts
import com.example.lomanalyzer.storage.tables.Communities
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Импортирует датасет из JSON-файла в БД и привязывает к сессии.
 *
 * @param communityDao,authorDao,postDao,commentDao,linkDao DAO для записи сущностей и связей.
 * @param progressReporter публикация прогресса импорта в UI.
 * @param logger структурированный логгер событий сбора.
 */
class JsonDataImporter(
    private val communityDao: CommunityDao,
    private val authorDao: AuthorDao,
    private val postDao: PostDao,
    private val commentDao: CommentDao,
    private val linkDao: LinkDao,
    private val progressReporter: ProgressReporter,
    private val logger: Logger,
) {
    /** JSON-парсер: игнорирует неизвестные поля и допускает «нестрогий» синтаксис. */
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * Выполняет импорт датасета из файла в указанную сессию.
     *
     * @param sessionId id сессии анализа, к которой привязываются данные.
     * @param jsonPath путь к JSON-файлу с датасетом.
     * @return сводка по числу импортированных сущностей.
     * @throws IllegalStateException если файл не найден.
     */
    fun import(sessionId: Int, jsonPath: String): ImportResult {
        // Проверяем наличие файла до начала разбора
        val file = File(jsonPath)
        if (!file.exists()) error("Файл импорта не найден: $jsonPath")

        // Логируем начало импорта как фазу сбора данных
        logger.event(AppEvent.COLLECTION_STARTED, mapOf(
            "session_id" to sessionId,
            "phase" to "JSON_IMPORT",
            "file" to jsonPath,
        ))

        // Разбираем весь файл в типизированную модель датасета
        val dataset = json.decodeFromString<ImportDataset>(file.readText())

        // Шаги 1-4. Прогресс со счётчиками публикует каждый шаг по ходу работы
        // (см. reportProgress) — событие только с текстом этапа оставляло бы
        // индикатор на нуле, так как долю экран считает по completedItems/totalItems.
        val communityMap = importCommunities(sessionId, dataset.communities)
        val authorMap = importAuthors(sessionId, dataset.authors)
        val postCount = importPosts(sessionId, dataset.posts)
        // Комментарии привязываются к уже загруженным постам
        val commentCount = importComments(sessionId, dataset.comments)

        val result = ImportResult(
            communities = communityMap.size,
            authors = authorMap.size,
            posts = postCount,
            comments = commentCount,
        )

        logger.event(AppEvent.COLLECTION_COMPLETED, mapOf(
            "session_id" to sessionId,
            "phase" to "JSON_IMPORT",
            "communities" to result.communities,
            "authors" to result.authors,
            "posts" to result.posts,
        ))
        logger.warn("JSON import complete: ${result.communities} communities, " +
            "${result.authors} authors, ${result.posts} posts")

        return result
    }

    /**
     * Импортирует сообщества: переиспользует существующие по vkId или вставляет
     * новые, связывает каждое с сессией.
     * @return отображение vkId сообщества в его id в БД.
     */
    private fun importCommunities(
        sessionId: Int,
        communities: List<ImportCommunity>,
    ): Map<Int, Int> {
        val vkIdToDbId = mutableMapOf<Int, Int>()
        for (community in communities) {
            // Если сообщество с таким vkId уже есть — берём его id, иначе вставляем новое
            val existing = communityDao.findByVkId(community.vkId)
            val dbId = if (existing != null) {
                existing[Communities.id].value
            } else {
                communityDao.insert(
                    vkId = community.vkId,
                    name = community.name,
                    screenName = community.screenName,
                    membersCount = community.membersCount,
                    isClosed = community.isClosed,
                    communityType = community.type,
                )
            }
            vkIdToDbId[community.vkId] = dbId
            // Привязываем сообщество к текущей сессии анализа
            linkDao.linkSessionCommunity(sessionId, dbId)
            reportProgress("Импорт сообществ", vkIdToDbId.size, communities.size)
        }
        return vkIdToDbId
    }

    /**
     * Импортирует авторов: переиспользует существующих по vkId или вставляет новых
     * с пометкой источника IMPORT, связывает каждого с сессией.
     * @return отображение vkId автора в его id в БД.
     */
    private fun importAuthors(
        sessionId: Int,
        authors: List<ImportAuthor>,
    ): Map<Int, Int> {
        val vkIdToDbId = mutableMapOf<Int, Int>()
        for (author in authors) {
            // Дедуп по vkId: существующего автора переиспользуем, нового вставляем
            val existing = authorDao.findByVkId(author.vkId)
            val dbId = if (existing != null) {
                existing[com.example.lomanalyzer.storage.tables.Authors.id].value
            } else {
                authorDao.insert(
                    vkId = author.vkId,
                    firstName = author.firstName,
                    lastName = author.lastName,
                    screenName = author.screenName,
                    followersCount = author.followersCount,
                    isClosed = author.isClosed,
                    discoverySource = "IMPORT",
                )
            }
            vkIdToDbId[author.vkId] = dbId
            // Привязываем автора к текущей сессии анализа
            linkDao.linkSessionAuthor(sessionId, dbId)
            reportProgress("Импорт авторов", vkIdToDbId.size, authors.size)
        }
        return vkIdToDbId
    }

    /**
     * Вставляет посты сессии. Длина собственного текста (ownTextLength) берётся
     * как длина text — используется далее в оценке оригинальности.
     * @return число вставленных постов.
     */
    private fun importPosts(sessionId: Int, posts: List<ImportPost>): Int {
        var count = 0
        for (post in posts) {
            postDao.insert(
                sessionId = sessionId,
                vkId = post.vkId,
                ownerId = post.ownerId,
                fromId = post.fromId,
                publishedAt = post.date,
                text = post.text,
                window = post.window,
                ownTextLength = post.text.length,
                likes = post.likes,
                reposts = post.reposts,
                comments = post.comments,
                views = post.views,
                containsMedia = post.containsMedia,
                hasCopyHistory = post.hasCopyHistory,
            )
            count++
            reportProgress("Импорт постов", count, posts.size)
        }
        return count
    }

    /**
     * Импортирует комментарии: для каждого находит соответствующий пост в БД по
     * паре vkId + ownerId в рамках сессии; если поста нет — комментарий пропускается.
     * @return число вставленных комментариев.
     */
    private fun importComments(sessionId: Int, comments: List<ImportComment>): Int {
        // Посты читаем один раз и индексируем по паре (vkId, ownerId) — именно по
        // ней комментарий ссылается на пост. Раньше findBySession стоял внутри
        // цикла, то есть таблица постов сессии перечитывалась целиком на каждый
        // комментарий: на 2660 комментариев и 1026 постов это 2660 выборок и
        // порядка 2.7 млн сравнений, что и составляло почти всё время импорта.
        val postIdByVkKey = postDao.findBySession(sessionId)
            .associate { (it[Posts.vkId] to it[Posts.ownerId]) to it[Posts.id].value }

        var count = 0
        for ((index, comment) in comments.withIndex()) {
            // Нет соответствующего поста — комментарий пропускаем
            val postId = postIdByVkKey[comment.postVkId to comment.postOwnerId] ?: continue
            commentDao.insert(
                sessionId = sessionId,
                postId = postId,
                vkId = comment.vkId,
                fromId = comment.fromId,
                text = comment.text.ifBlank { null }, // пустой текст храним как null
                publishedAt = comment.date,
                likes = comment.likes,
            )
            count++
            // Прогресс считаем по просмотренным, а не по вставленным: пропуски
            // иначе оставили бы индикатор недокрученным до конца.
            reportProgress("Импорт комментариев", index + 1, comments.size)
        }
        return count
    }

    /**
     * Публикует прогресс импорта со счётчиками.
     *
     * Счётчики обязательны: экран сбора считает долю как completedItems/totalItems
     * и при totalItems = 0 показывает 0 %. Раньше импорт сообщал только текст этапа,
     * поэтому индикатор всю загрузку простаивал на нуле.
     *
     * Чтобы не публиковать событие на каждую строку, отчёт идёт раз в
     * PROGRESS_EVERY элементов и обязательно на последнем.
     */
    private fun reportProgress(stage: String, done: Int, total: Int) {
        if (done % PROGRESS_EVERY == 0 || done == total) {
            progressReporter.update(ProgressEvent(
                stage = stage,
                completedItems = done,
                totalItems = total,
            ))
        }
    }

    companion object {
        /** Частота публикации прогресса — раз в N обработанных элементов. */
        private const val PROGRESS_EVERY = 50
    }

    /** Сводка результатов импорта: число загруженных сообществ, авторов, постов и комментариев. */
    data class ImportResult(
        val communities: Int,
        val authors: Int,
        val posts: Int,
        val comments: Int = 0,
    )
}
