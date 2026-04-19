package com.example.lomanalyzer.security

/**
 * PII masking per v6 §27.1.1:
 * - vk_id: 123456789 → vk_id: 1****89
 * - screen_name: ivanov123 → screen_name: iv*****23
 * - firstName/lastName/first_name/last_name → ***
 * - Text fields > 80 chars → <redacted:{length}>
 */
object PiiSafeFormatter {

    private val VK_ID_PATTERN = Regex(
        """("?vk_id"?\s*[:=]\s*["']?)(\d{3,})(["']?)""",
        RegexOption.IGNORE_CASE,
    )
    private val SCREEN_NAME_PATTERN = Regex(
        """("?screen_name"?\s*[:=]\s*["']?)([a-zA-Z0-9_]{4,})(["']?)""",
        RegexOption.IGNORE_CASE,
    )
    private val NAME_FIELD_PATTERN = Regex(
        """("?(?:first_?name|last_?name|firstName|lastName)"?\s*[:=]\s*["']?)([^"',}\]\s]+)(["']?)""",
        RegexOption.IGNORE_CASE,
    )

    fun mask(input: String): String {
        var result = input
        result = maskVkIds(result)
        result = maskScreenNames(result)
        result = maskNameFields(result)
        result = maskLongTextFields(result)
        return result
    }

    fun maskVkIds(input: String): String =
        VK_ID_PATTERN.replace(input) { match ->
            val prefix = match.groupValues[1]
            val id = match.groupValues[2]
            val suffix = match.groupValues[3]
            val masked = maskKeepFirstLast(id, 1, 2)
            "$prefix$masked$suffix"
        }

    fun maskScreenNames(input: String): String =
        SCREEN_NAME_PATTERN.replace(input) { match ->
            val prefix = match.groupValues[1]
            val name = match.groupValues[2]
            val suffix = match.groupValues[3]
            val masked = maskKeepFirstLast(name, 2, 2)
            "$prefix$masked$suffix"
        }

    fun maskNameFields(input: String): String =
        NAME_FIELD_PATTERN.replace(input) { match ->
            val prefix = match.groupValues[1]
            val suffix = match.groupValues[3]
            "$prefix***$suffix"
        }

    fun maskLongTextFields(input: String): String {
        // Match JSON string values > 80 chars in "key": "value" patterns
        val jsonStringPattern = Regex("""("(?:text|description|message|content)":\s*")([^"]{81,})"""")
        return jsonStringPattern.replace(input) { match ->
            val prefix = match.groupValues[1]
            val value = match.groupValues[2]
            """${prefix}<redacted:${value.length}>""""
        }
    }

    private fun maskKeepFirstLast(value: String, keepFirst: Int, keepLast: Int): String {
        if (value.length <= keepFirst + keepLast) return "*".repeat(value.length)
        val stars = "*".repeat(value.length - keepFirst - keepLast)
        return value.take(keepFirst) + stars + value.takeLast(keepLast)
    }
}
