package com.example.lomanalyzer.analysis.lom

/**
 * If Author.isClosed or followersCount unknown, impute F = Q25(F_open).
 * Flag audienceFlag = ESTIMATED_CONSERVATIVE.
 * Imputed values are excluded from normalization statistics.
 */
object ClosedProfileImputer {
    fun computeQ25(openFollowerCounts: List<Int>): Int {
        if (openFollowerCounts.isEmpty()) return 0
        val sorted = openFollowerCounts.sorted()
        val idx = (sorted.size * 0.25).toInt().coerceIn(0, sorted.size - 1)
        return sorted[idx]
    }

    fun impute(
        followersCount: Int?,
        isClosed: Boolean,
        q25: Int,
    ): ImputeResult {
        if (isClosed || followersCount == null || followersCount <= 0) {
            return ImputeResult(
                followers = q25,
                audienceFlag = "ESTIMATED_CONSERVATIVE",
                imputed = true,
            )
        }
        return ImputeResult(
            followers = followersCount,
            audienceFlag = "NORMAL",
            imputed = false,
        )
    }
}

data class ImputeResult(
    val followers: Int,
    val audienceFlag: String,
    val imputed: Boolean,
)
