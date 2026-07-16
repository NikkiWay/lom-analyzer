/*
 * НАЗНАЧЕНИЕ
 * Низкоуровневый клиент VK API — единая точка для всех HTTP-вызовов методов VK
 * (модуль сбора данных, этапы 2-4 алгоритма: тематические посты, реестр авторов,
 * фоновые/тематические посты со стен, комментарии). Каждый публичный метод
 * соответствует одному методу VK API и формирует его GET-запрос с нужными
 * параметрами.
 *
 * ЧТО ВНУТРИ
 * Класс VkApiClient с обёртками над методами VK:
 *  - wall.get — посты со стены пользователя/сообщества (пагинация через offset);
 *  - users.get / users.search — профили и поиск пользователей;
 *  - groups.search / groups.getById — поиск и получение сообществ;
 *  - newsfeed.search — поиск постов по ключевым словам во всём VK;
 *  - wall.getComments — комментарии под постом (пагинация через offset);
 *  - likes.getList — список пользователей, репостнувших пост;
 *  - utils.resolveScreenName — разрешение короткого имени в числовой id;
 *  - execute — пакетный вызов нескольких методов одним запросом (VKScript).
 * Приватный generic-метод request() инкапсулирует общий путь запроса.
 *
 * ФРЕЙМВОРКИ
 * Ktor client (HttpClient, GET-запросы) — транспорт HTTP; kotlinx.serialization
 * (через response.body() и модели VkResponse) — разбор JSON-ответов VK.
 *
 * СВЯЗИ
 * Все запросы проходят через VkRateLimiter (ограничение частоты, rate limit) и
 * VkBackoff (повтор при HTTP 429 и 5xx с экспоненциальной задержкой). Клиент
 * используют коллекторы (CurrentCollector, BaselineCollector, AuthorWallCollector,
 * CommentCollector, NewsfeedSearchCollector, AuthorProfileCollector) и
 * PaginationManager.
 */
package com.example.lomanalyzer.vk

import com.example.lomanalyzer.vk.models.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*

/**
 * Клиент VK API: формирует и выполняет запросы к методам VK.
 *
 * @param httpClient Ktor HttpClient — транспорт для HTTP GET-запросов.
 * @param rateLimiter ограничитель частоты запросов (rate limit), вызывается перед каждым запросом.
 * @param backoff стратегия повторов при временных сбоях (HTTP 429 / 5xx).
 * @param apiVersion версия VK API (параметр v), по умолчанию 5.199.
 */
class VkApiClient(
    private val httpClient: HttpClient,
    private val rateLimiter: VkRateLimiter,
    private val backoff: VkBackoff,
    private val apiVersion: String = "5.199",
) {
    companion object {
        /** Базовый URL всех методов VK API. */
        private const val BASE_URL = "https://api.vk.com/method"
    }

    /**
     * wall.get — посты со стены пользователя или сообщества.
     * Для сообществ owner_id передаётся отрицательным. Пагинация — через offset/count.
     * @param ownerId id владельца стены (отрицательный для сообществ).
     * @param offset смещение от начала ленты постов (pagination).
     * @param count число постов на страницу (VK ограничивает максимум 100).
     */
    suspend fun wallGet(
        ownerId: Int,
        offset: Int,
        count: Int,
        accessToken: String,
    ): VkResponse<VkWallResponse> = request("wall.get") {
        // Параметры запроса VK: чья стена, с какого смещения и сколько постов
        parameter("owner_id", ownerId)
        parameter("offset", offset)
        parameter("count", count)
        parameter("access_token", accessToken)
    }

    /**
     * users.get — профили пользователей по их id (батч до 1000 id за вызов).
     * Запрашиваются доп. поля followers_count, screen_name, is_closed для оценок и фильтрации.
     */
    suspend fun usersGet(
        userIds: List<Int>,
        accessToken: String,
    ): VkResponse<List<VkUser>> = request("users.get") {
        // VK ожидает user_ids как строку через запятую
        parameter("user_ids", userIds.joinToString(","))
        parameter("fields", "followers_count,screen_name,is_closed")
        parameter("access_token", accessToken)
    }

    /**
     * groups.search — поиск сообществ по строке запроса.
     * @param sort порядок сортировки результатов (6 — по числу участников).
     * Необязательные фильтры (тип, город, страна) добавляются только если заданы.
     */
    suspend fun groupsSearch(
        query: String,
        count: Int = 20,
        offset: Int = 0,
        type: String? = null,
        cityId: Int? = null,
        countryId: Int? = null,
        sort: Int = 6,
        accessToken: String,
    ): VkResponse<VkGroupSearchResponse> = request("groups.search") {
        parameter("q", query)
        parameter("count", count)
        // Необязательные параметры добавляем только при наличии значения
        if (offset > 0) parameter("offset", offset)
        if (type != null) parameter("type", type)
        if (cityId != null) parameter("city_id", cityId)
        if (countryId != null) parameter("country_id", countryId)
        parameter("sort", sort)
        parameter("fields", "members_count,screen_name,is_closed,description,activity")
        parameter("access_token", accessToken)
    }

    /**
     * users.search — поиск пользователей по строке запроса с опциональными гео-фильтрами.
     */
    suspend fun usersSearch(
        query: String,
        count: Int = 20,
        cityId: Int? = null,
        countryId: Int? = null,
        sort: Int = 0,
        accessToken: String,
    ): VkResponse<VkUserSearchResponse> = request("users.search") {
        parameter("q", query)
        parameter("count", count)
        if (cityId != null) parameter("city", cityId)
        if (countryId != null) parameter("country", countryId)
        parameter("sort", sort)
        parameter("fields", "followers_count,screen_name,is_closed,city")
        parameter("access_token", accessToken)
    }

    /**
     * newsfeed.search — поиск постов по ключевым словам по всему VK (этап 2, режим без сообществ).
     * Особенности метода: по умолчанию возвращает только последние сутки, поэтому
     * обязательны start_time/end_time; пагинация — через непрозрачный курсор start_from
     * (следующая страница в next_from); максимум 200 постов на страницу и не более 1000
     * за один диапазон. Сегментацию по датам выполняет NewsfeedSearchCollector.
     * @param startFrom курсор следующей страницы (pagination), null для первой страницы.
     * @param startTime/endTime границы диапазона дат (Unix-время, секунды).
     */
    suspend fun newsfeedSearch(
        query: String,
        count: Int = 200,
        startFrom: String? = null,
        startTime: Long? = null,
        endTime: Long? = null,
        accessToken: String,
    ): VkResponse<VkNewsfeedSearchResponse> = request("newsfeed.search") {
        parameter("q", query)
        parameter("count", count)
        parameter("extended", 0)
        // Курсор пагинации и временные границы диапазона добавляем при наличии
        if (startFrom != null) parameter("start_from", startFrom)
        if (startTime != null) parameter("start_time", startTime)
        if (endTime != null) parameter("end_time", endTime)
        parameter("access_token", accessToken)
    }

    /**
     * groups.getById — данные сообществ по их числовым id (обёртка над строковым вариантом).
     */
    suspend fun groupsGetById(
        groupIds: List<Int>,
        accessToken: String,
    ): VkResponse<List<VkGroup>> = groupsGetByStringIds(
        groupIds.joinToString(","), accessToken,
    )

    /**
     * groups.getById — данные сообществ по строке id через запятую.
     * VK возвращает объект с полем groups; здесь ответ распаковывается до списка VkGroup,
     * чтобы вызывающий код работал с привычным VkResponse<List<VkGroup>>.
     */
    suspend fun groupsGetByStringIds(
        groupIds: String,
        accessToken: String,
    ): VkResponse<List<VkGroup>> {
        val raw: VkResponse<VkGroupsGetByIdResponse> = request("groups.getById") {
            parameter("group_ids", groupIds)
            parameter("fields", "members_count,screen_name,is_closed,description,activity")
            parameter("access_token", accessToken)
        }
        // Перепаковываем вложенный объект .groups в плоский список, сохраняя возможную ошибку
        return VkResponse(
            response = raw.response?.groups,
            error = raw.error,
        )
    }

    /**
     * utils.resolveScreenName — разрешает короткое имя (screen_name) в тип объекта и числовой id.
     */
    suspend fun resolveScreenName(
        screenName: String,
        accessToken: String,
    ): VkResponse<VkResolvedScreenName> = request("utils.resolveScreenName") {
        parameter("screen_name", screenName)
        parameter("access_token", accessToken)
    }

    /**
     * users.get с передачей строковых идентификаторов (в т.ч. screen_name) через запятую.
     */
    suspend fun usersGetByScreenNames(
        userIds: String,
        accessToken: String,
    ): VkResponse<List<VkUser>> = request("users.get") {
        parameter("user_ids", userIds)
        parameter("fields", "followers_count,screen_name,is_closed")
        parameter("access_token", accessToken)
    }

    /**
     * wall.getComments — комментарии под конкретным постом. Пагинация — через offset/count.
     * @param postId id поста, под которым собираются комментарии.
     * @param sort порядок ("asc"/"desc"); need_likes=1 запрашивает счётчики лайков комментариев.
     */
    suspend fun wallGetComments(
        ownerId: Int,
        postId: Int,
        offset: Int = 0,
        count: Int = 100,
        sort: String = "asc",
        accessToken: String,
    ): VkResponse<VkWallCommentsResponse> = request("wall.getComments") {
        parameter("owner_id", ownerId)
        parameter("post_id", postId)
        parameter("offset", offset)
        parameter("count", count)
        parameter("sort", sort)
        parameter("need_likes", 1) // запросить число лайков по каждому комментарию
        parameter("access_token", accessToken)
    }

    /**
     * likes.getList — список id пользователей, отметивших объект (здесь — репостнувших пост).
     * filter="copies" возвращает именно тех, кто сделал репост.
     */
    suspend fun likesGetList(
        ownerId: Int,
        itemId: Int,
        accessToken: String,
    ): VkResponse<VkLikesListResponse> = request("likes.getList") {
        parameter("type", "post")
        parameter("owner_id", ownerId)
        parameter("item_id", itemId)
        parameter("filter", "copies") // только сделавшие репост
        parameter("access_token", accessToken)
    }

    /**
     * execute — пакетный вызов: один HTTP-запрос выполняет VKScript-код, объединяющий
     * до 25 обращений к API. Снижает число запросов и нагрузку на rate limit.
     * @param code тело VKScript (формируется в VkExecuteBatcher).
     */
    suspend fun execute(
        code: String,
        accessToken: String,
    ): VkExecuteResponse {
        // Учитываем запрос в ограничителе частоты перед отправкой
        rateLimiter.acquire()
        // Повтор при временных сбоях (429/5xx) выполняет backoff
        return backoff.withRetry {
            val response: HttpResponse = httpClient.get("$BASE_URL/execute") {
                parameter("code", code)
                parameter("access_token", accessToken)
                parameter("v", apiVersion)
            }
            response.body()
        }
    }

    /**
     * Общий путь любого запроса к VK API: применяет rate limit и backoff, формирует
     * GET-запрос к BASE_URL/method с обязательным параметром версии v и переданными params,
     * затем десериализует тело ответа в тип T (через kotlinx.serialization).
     * @param method имя метода VK (например, "wall.get").
     * @param params лямбда, добавляющая специфичные для метода параметры запроса.
     */
    private suspend inline fun <reified T> request(
        method: String,
        crossinline params: HttpRequestBuilder.() -> Unit,
    ): T {
        // 1) Соблюдаем лимит частоты запросов (rate limit)
        rateLimiter.acquire()
        // 2) Выполняем запрос с автоматическим повтором при HTTP 429/5xx
        return backoff.withRetry {
            val response: HttpResponse = httpClient.get("$BASE_URL/$method") {
                parameter("v", apiVersion) // обязательная версия VK API
                params() // параметры конкретного метода
            }
            response.body() // разбор JSON-ответа в тип T
        }
    }
}
