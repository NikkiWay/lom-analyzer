/*
 * НАЗНАЧЕНИЕ
 * Тестовая утилита (fixture-помощник) для интеграционных тестов: разбирает JSON
 * минимального тестового корпуса и загружает его в БД — создаёт сессию, авторов
 * и посты. Используется в MvpSmokeTest для подготовки данных пайплайна.
 *
 * ЧТО ВНУТРИ
 * DTO-модели формата корпуса: TestPost, TestAuthor, TestCorpus (десериализуемые
 * kotlinx.serialization). Класс TestCorpusImporter с методом import: парсинг JSON
 * и вставка записей через DAO-слой; возвращает идентификатор созданной сессии.
 *
 * БИБЛИОТЕКИ
 * kotlinx.serialization — разбор JSON; аннотация @SerialName сопоставляет
 * snake_case-поля JSON с camelCase-свойствами Kotlin. Exposed ORM — доступ к БД
 * через DAO. Json настроен с ignoreUnknownKeys (терпим к лишним полям корпуса).
 *
 * СВЯЗИ
 * Использует SessionDao, AuthorDao, PostDao. Поставляет данные для MvpSmokeTest.
 */
package com.example.lomanalyzer.integration

import com.example.lomanalyzer.storage.dao.AuthorDao
import com.example.lomanalyzer.storage.dao.PostDao
import com.example.lomanalyzer.storage.dao.SessionDao
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database

/**
 * DTO одного поста тестового корпуса. Поля в JSON заданы в snake_case и
 * сопоставлены через @SerialName: from_id, published_at (Unix-время в секундах),
 * text_clean (очищенный текст), own_text_length (длина собственного текста),
 * has_copy_history (признак репоста), contains_media (наличие вложений).
 */
@Serializable
data class TestPost(
    val id: Int,
    @SerialName("from_id") val fromId: Int,
    @SerialName("published_at") val publishedAt: Long,
    @SerialName("text_clean") val textClean: String,
    val likes: Int,
    val reposts: Int,
    val comments: Int,
    @SerialName("own_text_length") val ownTextLength: Int,
    @SerialName("has_copy_history") val hasCopyHistory: Boolean,
    @SerialName("contains_media") val containsMedia: Boolean,
)

/**
 * DTO одного автора тестового корпуса: хэшированный VK-идентификатор, число
 * подписчиков, признак закрытого профиля и источник обнаружения автора.
 */
@Serializable
data class TestAuthor(
    val id: Int,
    @SerialName("vk_id_hashed") val vkIdHashed: String,
    @SerialName("followers_count") val followersCount: Int,
    @SerialName("is_closed") val isClosed: Boolean,
    @SerialName("discovery_source") val discoverySource: String,
)

/**
 * Корневой DTO корпуса: версия формата, список постов и список авторов.
 */
@Serializable
data class TestCorpus(
    val version: String,
    val posts: List<TestPost>,
    val authors: List<TestAuthor>,
)

/**
 * Импортёр тестового корпуса в БД.
 * @param db подключение Exposed к целевой (обычно временной тестовой) базе.
 */
class TestCorpusImporter(private val db: Database) {
    /** Парсер JSON; ignoreUnknownKeys — игнорировать поля корпуса, не описанные в DTO. */
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Разбирает JSON корпуса и загружает его в БД: создаёт сессию, затем вставляет
     * всех авторов и все посты.
     * @param corpusJson содержимое JSON-файла корпуса.
     * @return идентификатор созданной сессии (используется тестом для выборок).
     */
    fun import(corpusJson: String): Int {
        // Десериализуем JSON в типизированную модель корпуса
        val corpus = json.decodeFromString<TestCorpus>(corpusJson)
        val sessionDao = SessionDao(db)
        val authorDao = AuthorDao(db)
        val postDao = PostDao(db)

        // Создаём синтетическую сессию-обёртку для данных корпуса
        val sessionId = sessionDao.insert(
            name = "Test Corpus Session",
            topicQuery = "ecology",
            region = "synthetic",
        )

        // Вставляем авторов корпуса
        for (author in corpus.authors) {
            authorDao.insert(
                vkId = author.id,
                followersCount = author.followersCount,
                isClosed = author.isClosed,
                discoverySource = author.discoverySource,
            )
        }

        // Вставляем посты корпуса в созданную сессию
        for (post in corpus.posts) {
            postDao.insert(
                sessionId = sessionId,
                vkId = post.id,
                ownerId = -1,
                fromId = post.fromId,
                // Время корпуса — в секундах, в БД храним в миллисекундах
                publishedAt = post.publishedAt * 1000,
                text = post.textClean,
                window = "BASELINE",
                ownTextLength = post.ownTextLength,
            )
        }

        return sessionId
    }
}
