package com.example.lomanalyzer.tools.quality

/**
 * Cohen's kappa for inter-rater agreement per v6 §25.3.
 */
object CohenKappa {
    fun compute(rater1: List<String>, rater2: List<String>): Double {
        require(rater1.size == rater2.size)
        val n = rater1.size.toDouble()
        if (n == 0.0) return 0.0

        val categories = (rater1 + rater2).distinct()
        val observed = rater1.zip(rater2).count { (a, b) -> a == b } / n

        var expected = 0.0
        for (cat in categories) {
            val p1 = rater1.count { it == cat } / n
            val p2 = rater2.count { it == cat } / n
            expected += p1 * p2
        }

        return if (expected >= 1.0) 1.0 else (observed - expected) / (1.0 - expected)
    }
}
