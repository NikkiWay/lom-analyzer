/*
 * НАЗНАЧЕНИЕ
 * Публичный контракт (интерфейс) подсистемы сбора данных — модуля 2 архитектуры
 * (диплом 2.2.3, 2.2.4–2.2.5). Описывает три фазы сбора (этапы 2–4 алгоритма) и
 * альтернативный импорт сессии из JSON без обращения к VK API.
 *
 * ЧТО ВНУТРИ
 * Интерфейс CollectionService с четырьмя suspend-методами (корутины): сбор
 * тематических постов, построение реестра авторов, сбор данных по каждому автору
 * и импорт из JSON.
 *
 * СВЯЗИ
 * Реализация живёт в пакете сбора (vk/, import/). Модуль общается с остальными
 * частями только через SQLite (изоляция модулей, см. docs/architecture.md).
 * Все методы — suspend, так как сбор сетевой и выполняется в корутинах.
 */
package com.example.lomanalyzer.core

/**
 * Public contract for the data collection subsystem (diploma 2.2.3, module 2).
 * Communicates with other modules only through SQLite.
 *
 * Публичный контракт подсистемы сбора данных (модуль 2). С другими модулями
 * взаимодействует только через SQLite.
 *
 * Implementation: collect/ package (stages 2-4 of the algorithm).
 */
interface CollectionService {
    /**
     * Этап 2: сбор тематических постов по ключевым словам за период сессии.
     *
     * Stage 2: Collect topic posts by keywords for the given session period
     *
     * @param sessionId идентификатор сессии анализа, для которой ведётся сбор.
     */
    suspend fun collectTopicPosts(sessionId: Int)

    /**
     * Этап 3: построение реестра авторов = все авторы (fromId) тематических постов.
     *
     * Stage 3: Build author registry = all authors of topic posts
     *
     * @param sessionId идентификатор сессии анализа.
     */
    suspend fun buildAuthorRegistry(sessionId: Int)

    /**
     * Этап 4: по каждому автору собрать профиль, фоновые посты, тематические посты и комментарии.
     *
     * Stage 4: For each author collect profile, background posts, topic posts, comments
     *
     * @param sessionId идентификатор сессии анализа.
     */
    suspend fun collectAuthorData(sessionId: Int)

    /**
     * Альтернатива сбору через VK API: импорт целой сессии из JSON-файла.
     *
     * Alternative: import entire session from JSON without VK API
     *
     * @param sessionId идентификатор сессии, в которую загружаются данные.
     * @param jsonPath путь к JSON-файлу с данными сессии.
     */
    suspend fun importFromJson(sessionId: Int, jsonPath: String)
}
