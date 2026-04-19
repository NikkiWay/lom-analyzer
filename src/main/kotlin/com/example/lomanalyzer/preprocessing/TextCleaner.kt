package com.example.lomanalyzer.preprocessing

data class CleanResult(
    val cleanText: String,
    val hashtags: List<String>,
    val truncated: Boolean,
    val truncationReason: String? = null,
    val urlsCount: Int = 0,
    val mentionsCount: Int = 0,
    val hashtagsCount: Int = 0,
)

object TextCleaner {
    private const val MAX_LENGTH = 15_000

    private val HTML_TAG_REGEX = Regex("<[^>]+>")
    private val URL_REGEX = Regex("https?://\\S+")
    private val MENTION_REGEX = Regex("@[\\w.]+")
    private val HASHTAG_REGEX = Regex("#[\\wА-яёЁ]+")
    private val MULTI_SPACE_REGEX = Regex("\\s+")

    fun clean(rawText: String): CleanResult {
        // Extract hashtags before cleaning
        val hashtags = HASHTAG_REGEX.findAll(rawText)
            .map { it.value.removePrefix("#") }
            .toList()

        val urlsCount = URL_REGEX.findAll(rawText).count()
        val mentionsCount = MENTION_REGEX.findAll(rawText).count()

        var text = rawText
        text = HTML_TAG_REGEX.replace(text, "")
        text = URL_REGEX.replace(text, "[URL]")
        text = MENTION_REGEX.replace(text, "[USER]")
        text = MULTI_SPACE_REGEX.replace(text, " ").trim()

        // Truncation
        var truncated = false
        var reason: String? = null
        if (text.length > MAX_LENGTH) {
            text = text.substring(0, MAX_LENGTH)
            truncated = true
            reason = "TEXT_EXCEEDS_${MAX_LENGTH}_CHARS"
        }

        return CleanResult(
            cleanText = text,
            hashtags = hashtags,
            truncated = truncated,
            truncationReason = reason,
            urlsCount = urlsCount,
            mentionsCount = mentionsCount,
            hashtagsCount = hashtags.size,
        )
    }
}
