package com.example.lomanalyzer.analysis.topic

object ExclusionFilter {
    fun isExcluded(matchResult: NgramMatchResult): Boolean =
        matchResult.excludedHit
}
