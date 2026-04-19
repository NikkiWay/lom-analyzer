package com.example.lomanalyzer.vk

import kotlinx.serialization.json.JsonElement

data class BatchedCall(
    val method: String,
    val params: Map<String, String>,
)

class VkExecuteBatcher(
    private val apiClient: VkApiClient,
    private val maxBatchSize: Int = 25,
) {
    suspend fun executeBatch(
        calls: List<BatchedCall>,
        accessToken: String,
    ): List<JsonElement?> {
        val results = mutableListOf<JsonElement?>()

        for (chunk in calls.chunked(maxBatchSize)) {
            val code = buildExecuteCode(chunk)
            val response = apiClient.execute(code, accessToken)
            val items = response.response ?: emptyList()
            // Pad with nulls if VK returned fewer results
            for (i in chunk.indices) {
                results.add(items.getOrNull(i))
            }
        }

        return results
    }

    private fun buildExecuteCode(calls: List<BatchedCall>): String {
        val sb = StringBuilder("var results = [];\n")
        for (call in calls) {
            val paramsStr = call.params.entries.joinToString(", ") { (k, v) ->
                "\"$k\": \"${escapeVkScript(v)}\""
            }
            sb.append("results.push(API.${call.method}({$paramsStr}));\n")
        }
        sb.append("return results;")
        return sb.toString()
    }

    private fun escapeVkScript(value: String): String =
        value.replace("\\", "\\\\").replace("\"", "\\\"")
}
