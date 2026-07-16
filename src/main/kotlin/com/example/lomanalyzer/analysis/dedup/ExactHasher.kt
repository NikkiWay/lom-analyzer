/*
 * НАЗНАЧЕНИЕ
 * Первый этап дедупликации (Stage 1, см. docs/algorithm.md): обнаружение точных
 * дубликатов по SHA-256-хешу нормализованного текста. Применяется ко ВСЕМ постам
 * с длиной собственного текста >= 30 для глобального выявления DETECTED_COPY.
 *
 * ЧТО ВНУТРИ
 * - object ExactHasher: нормализация и хеширование текста (normalizeAndHash),
 *   проверка пригодности (isEligible), группировка постов по хешу
 *   (findDuplicateGroups).
 * - data class HashablePost: «лёгкая» проекция поста для дедупликации.
 *
 * МЕТОД
 * Текст нормализуется (нижний регистр, удаление плейсхолдеров [url]/[user],
 * хештегов, схлопывание пробелов), затем считается SHA-256. Совпадение хешей =
 * точный дубликат. В группе дубликатов лидером (GROUP_LEADER) становится самый
 * ранний по publishedAt пост, остальные — точные копии (EXACT_COPY).
 *
 * БИБЛИОТЕКИ
 * java.security.MessageDigest — вычисление SHA-256; kotlin.text.Regex — нормализация.
 *
 * СВЯЗИ
 * Вызывается из DedupPipeline (runStage1); результат влияет на классификацию
 * оригинальности (DETECTED_COPY) в OriginalityClassifier.
 */
package com.example.lomanalyzer.analysis.dedup

import java.security.MessageDigest

/**
 * Stage 1: SHA-256 exact-match deduplication.
 * Applied to ALL posts with ownTextLength >= 30 for global DETECTED_COPY detection.
 * First by publishedAt becomes GROUP_LEADER; others become EXACT_COPY.
 *
 * Точная дедупликация по SHA-256-хешу нормализованного текста.
 */
object ExactHasher {
    /** Минимальная длина собственного текста для участия в точной дедупликации. */
    private const val MIN_TEXT_LENGTH = 30

    /** Плейсхолдер ссылки, вставляемый при очистке текста (удаляется перед хешем). */
    private val URL_REGEX = Regex(Regex.escape("[url]"))
    /** Плейсхолдер упоминания пользователя (удаляется перед хешем). */
    private val USER_REGEX = Regex(Regex.escape("[user]"))
    /** Хештеги (латиница/кириллица) — удаляются перед хешем. */
    private val HASHTAG_REGEX = Regex("#[\\wА-яёЁ]+")
    /** Последовательности пробелов — схлопываются в один пробел. */
    private val MULTI_SPACE = Regex("\\s+")

    /** @return true, если пост достаточно длинный для точной дедупликации. */
    fun isEligible(ownTextLength: Int): Boolean =
        ownTextLength >= MIN_TEXT_LENGTH

    /**
     * Нормализует текст и возвращает его SHA-256-хеш в hex-виде.
     * Нормализация: нижний регистр, удаление [url]/[user]/хештегов,
     * схлопывание пробелов и обрезка краёв — чтобы косметические различия
     * не мешали обнаружению точных дубликатов.
     */
    fun normalizeAndHash(text: String): String {
        // Последовательная нормализация текста перед хешированием
        val normalized = text
            .lowercase()
            .let { URL_REGEX.replace(it, "") }
            .let { USER_REGEX.replace(it, "") }
            .let { HASHTAG_REGEX.replace(it, "") }
            .let { MULTI_SPACE.replace(it, " ") }
            .trim()

        // SHA-256 от UTF-8 байтов нормализованного текста
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(normalized.toByteArray(Charsets.UTF_8))
        // Перевод байтов хеша в шестнадцатеричную строку
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Groups posts by hash. Returns map of hash → list of (postId, publishedAt),
     * sorted by publishedAt ascending. First entry is the GROUP_LEADER.
     *
     * Группирует посты по хешу. Возвращает только группы с дубликатами (size > 1),
     * внутри каждой группы посты отсортированы по времени публикации по возрастанию
     * (первый — GROUP_LEADER).
     */
    fun findDuplicateGroups(
        posts: List<HashablePost>,
    ): Map<String, List<HashablePost>> {
        val hashMap = mutableMapOf<String, MutableList<HashablePost>>()

        // Раскладываем пригодные посты по корзинам одинакового хеша
        for (post in posts) {
            if (!isEligible(post.ownTextLength)) continue
            val hash = normalizeAndHash(post.cleanText)
            hashMap.getOrPut(hash) { mutableListOf() }.add(post)
        }

        // Only keep groups with duplicates (size > 1), sort by publishedAt
        // Оставляем только группы с дубликатами и сортируем каждую по времени публикации
        return hashMap
            .filter { it.value.size > 1 }
            .mapValues { (_, group) -> group.sortedBy { it.publishedAt } }
    }
}

/**
 * Облегчённая проекция поста для дедупликации.
 *
 * @param postId внутренний id поста.
 * @param fromId VK-идентификатор автора.
 * @param publishedAt время публикации (Unix epoch, секунды).
 * @param cleanText очищенный текст поста (для хеширования).
 * @param ownTextLength длина собственного текста.
 * @param isTopicRelevant признак тематической релевантности (null — неизвестно).
 */
data class HashablePost(
    val postId: Int,
    val fromId: Int,
    val publishedAt: Long,
    val cleanText: String,
    val ownTextLength: Int,
    val isTopicRelevant: Boolean?,
)
