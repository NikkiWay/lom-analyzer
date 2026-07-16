/*
 * НАЗНАЧЕНИЕ
 * Загрузка и валидация тестового корпуса (test corpus) с эталонной разметкой
 * (ground truth) для проверки качества пайплайна и анализа чувствительности.
 * Корпус содержит посты, авторов и эталонные метки (тематичность, тональность,
 * тип оригинальности, ожидаемые роли и аномалии).
 *
 * ЧТО ВНУТРИ
 * @Serializable data-классы модели корпуса: CorpusPost, CorpusGroundTruth,
 * CorpusAuthor, CorpusRoleGT, CorpusAnomalyGT, TestCorpusData. object
 * TestCorpusLoader — разбор JSON (load) и проверка целостности (validate).
 *
 * МЕТОД
 * validate проверяет ссылочную целостность: непустые посты/авторы, ссылки постов
 * и ролей на существующих авторов, наличие ground_truth у каждого поста.
 *
 * БИБЛИОТЕКИ
 * kotlinx.serialization (@Serializable, @SerialName, Json) — разбор JSON.
 *
 * СВЯЗИ
 * Используется инструментами качества и анализа чувствительности как источник
 * эталонных данных.
 */
package com.example.lomanalyzer.tools.sensitivity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Пост тестового корпуса с метриками и опциональной эталонной разметкой.
 *
 * @property fromId id автора поста (сопоставляется со списком авторов корпуса).
 * @property textClean очищенный текст поста.
 * @property ownTextLength длина собственного текста (для оценки оригинальности).
 * @property hasCopyHistory является ли пост репостом.
 * @property groundTruth эталонные метки поста (если заданы).
 */
@Serializable
data class CorpusPost(
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
    @SerialName("ground_truth") val groundTruth: CorpusGroundTruth? = null,
)

/**
 * Эталонная разметка поста.
 *
 * @property isTopicRelevant эталон тематической релевантности.
 * @property sentiment эталонная тональность (POSITIVE/NEGATIVE/NEUTRAL).
 * @property originalityType эталонный тип оригинальности (например, ORIGINAL, PURE_REPOST).
 */
@Serializable
data class CorpusGroundTruth(
    @SerialName("is_topic_relevant") val isTopicRelevant: Boolean? = null,
    val sentiment: String? = null,
    @SerialName("originality_type") val originalityType: String? = null,
)

/** Автор тестового корпуса (id, число подписчиков, закрытость, источник обнаружения). */
@Serializable
data class CorpusAuthor(
    val id: Int,
    @SerialName("followers_count") val followersCount: Int,
    @SerialName("is_closed") val isClosed: Boolean = false,
    @SerialName("discovery_source") val discoverySource: String = "SEED",
)

/** Эталонная роль автора (ожидаемая роль для проверки классификатора ролей). */
@Serializable
data class CorpusRoleGT(
    @SerialName("author_id") val authorId: Int,
    @SerialName("expected_role") val expectedRole: String,
)

/** Эталонная аномалия: дата и список ожидаемых типов аномалий на эту дату. */
@Serializable
data class CorpusAnomalyGT(
    val date: String,
    @SerialName("expected_types") val expectedTypes: List<String>,
)

/**
 * Корневой объект тестового корпуса: версия, посты, авторы и эталонные разметки
 * ролей и аномалий.
 */
@Serializable
data class TestCorpusData(
    val version: String,
    val posts: List<CorpusPost>,
    val authors: List<CorpusAuthor>,
    @SerialName("ground_truth_roles") val groundTruthRoles: List<CorpusRoleGT> = emptyList(),
    @SerialName("ground_truth_anomalies") val groundTruthAnomalies: List<CorpusAnomalyGT> = emptyList(),
)

/** Загрузчик и валидатор тестового корпуса из JSON. */
object TestCorpusLoader {
    /** JSON-парсер, игнорирующий неизвестные поля. */
    private val json = Json { ignoreUnknownKeys = true }

    /** Разбирает JSON-содержимое в модель тестового корпуса. */
    fun load(jsonContent: String): TestCorpusData =
        json.decodeFromString(jsonContent)

    /**
     * Проверяет целостность корпуса и возвращает список найденных проблем
     * (пустой список — корпус корректен).
     */
    fun validate(corpus: TestCorpusData): List<String> {
        val errors = mutableListOf<String>()

        // Корпус должен содержать хотя бы один пост и одного автора
        if (corpus.posts.isEmpty()) errors.add("No posts")
        if (corpus.authors.isEmpty()) errors.add("No authors")

        // Множество известных id авторов для проверки ссылок
        val authorIds = corpus.authors.map { it.id }.toSet()
        for (post in corpus.posts) {
            // Пост должен ссылаться на существующего автора
            if (post.fromId !in authorIds) {
                errors.add("Post ${post.id} references unknown author ${post.fromId}")
            }
            // У каждого поста должна быть эталонная разметка
            if (post.groundTruth == null) {
                errors.add("Post ${post.id} missing ground_truth")
            }
        }

        // Эталонные роли тоже должны ссылаться на существующих авторов
        for (role in corpus.groundTruthRoles) {
            if (role.authorId !in authorIds) {
                errors.add("Role GT references unknown author ${role.authorId}")
            }
        }

        return errors
    }
}
