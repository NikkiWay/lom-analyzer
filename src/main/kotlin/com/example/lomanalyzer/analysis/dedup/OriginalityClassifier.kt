package com.example.lomanalyzer.analysis.dedup

/**
 * Originality classification per v6 §14.2.4.
 *
 * | Type                | Condition                                              | Weight |
 * |---------------------|--------------------------------------------------------|--------|
 * | ORIGINAL            | No copy_history, ownTextLength >= 20, not DETECTED_COPY | 1.0    |
 * | REPOST_WITH_COMMENT | Has copy_history, ownTextLength >= 30                   | 0.5    |
 * | PURE_REPOST         | Has copy_history, ownTextLength < 30                    | 0.0    |
 * | DETECTED_COPY       | SHA-256 matches earlier post by different author         | 0.0    |
 * | MEDIA_ONLY          | No copy_history, ownTextLength < 20, containsMedia      | 0.25   |
 */
enum class OriginalityType(val weight: Float) {
    ORIGINAL(1.0f),
    REPOST_WITH_COMMENT(0.5f),
    PURE_REPOST(0.0f),
    DETECTED_COPY(0.0f),
    MEDIA_ONLY(0.25f),
}

object OriginalityClassifier {

    fun classify(
        hasCopyHistory: Boolean,
        ownTextLength: Int,
        containsMedia: Boolean,
        isDetectedCopy: Boolean,
    ): OriginalityType = when {
        isDetectedCopy -> OriginalityType.DETECTED_COPY
        hasCopyHistory && ownTextLength >= 30 -> OriginalityType.REPOST_WITH_COMMENT
        hasCopyHistory -> OriginalityType.PURE_REPOST
        ownTextLength >= 20 -> OriginalityType.ORIGINAL
        containsMedia -> OriginalityType.MEDIA_ONLY
        else -> OriginalityType.ORIGINAL // fallback for short non-media posts
    }
}
