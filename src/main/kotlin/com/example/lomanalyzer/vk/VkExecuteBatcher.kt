/*
 * НАЗНАЧЕНИЕ
 * Пакетирование множества вызовов VK API в один запрос через метод execute (VKScript).
 * Снижает число HTTP-запросов и нагрузку на rate limit при массовом сборе данных.
 *
 * ЧТО ВНУТРИ
 * data class BatchedCall (описание одного вложенного вызова: метод и его параметры) и
 * класс VkExecuteBatcher: разбивает список вызовов на пачки до 25, генерирует VKScript-код
 * и выполняет его через VkApiClient.execute.
 *
 * МЕТОД
 * VK execute принимает скрипт на VKScript; здесь формируется массив results, в который
 * по очереди складываются результаты вызовов API.method(...). VK ограничивает число
 * вложенных вызовов 25 за один execute — отсюда maxBatchSize.
 *
 * ФРЕЙМВОРКИ
 * kotlinx.serialization.json.JsonElement — сырые элементы результата (каждый вызов может
 * вернуть свой тип, разбор выполняется позже на стороне вызывающего кода).
 */
package com.example.lomanalyzer.vk

import kotlinx.serialization.json.JsonElement

/**
 * Описание одного вызова в пакете execute.
 * @param method имя метода VK (например, "users.get").
 * @param params параметры метода в виде строковых пар ключ-значение.
 */
data class BatchedCall(
    val method: String,
    val params: Map<String, String>,
)

/**
 * Пакетный исполнитель вызовов VK API через метод execute.
 * @param maxBatchSize максимум вложенных вызовов на один execute (VK ограничивает 25).
 */
class VkExecuteBatcher(
    private val apiClient: VkApiClient,
    private val maxBatchSize: Int = 25,
) {
    /**
     * Выполняет список вызовов пачками по maxBatchSize.
     * @return список результатов в исходном порядке вызовов; элемент null, если VK не вернул результат.
     */
    suspend fun executeBatch(
        calls: List<BatchedCall>,
        accessToken: String,
    ): List<JsonElement?> {
        val results = mutableListOf<JsonElement?>()

        // Разбиваем общий список на пачки по лимиту VK и выполняем каждую одним execute
        for (chunk in calls.chunked(maxBatchSize)) {
            val code = buildExecuteCode(chunk) // генерация VKScript для пачки
            val response = apiClient.execute(code, accessToken)
            val items = response.response ?: emptyList()
            // Дополняем null-ами, если VK вернул меньше результатов, чем вызовов
            for (i in chunk.indices) {
                results.add(items.getOrNull(i))
            }
        }

        return results
    }

    /**
     * Формирует VKScript для пачки: собирает массив results из вызовов API.method(params).
     * @return текст скрипта, возвращающего массив результатов.
     */
    private fun buildExecuteCode(calls: List<BatchedCall>): String {
        val sb = StringBuilder("var results = [];\n")
        for (call in calls) {
            // Сериализуем параметры вызова в объект VKScript "ключ": "значение"
            val paramsStr = call.params.entries.joinToString(", ") { (k, v) ->
                "\"$k\": \"${escapeVkScript(v)}\""
            }
            // Добавляем вызов метода и складываем его результат в массив
            sb.append("results.push(API.${call.method}({$paramsStr}));\n")
        }
        sb.append("return results;")
        return sb.toString()
    }

    /** Экранирует обратные слэши и кавычки в значении параметра для безопасной вставки в VKScript. */
    private fun escapeVkScript(value: String): String =
        value.replace("\\", "\\\\").replace("\"", "\\\"")
}
