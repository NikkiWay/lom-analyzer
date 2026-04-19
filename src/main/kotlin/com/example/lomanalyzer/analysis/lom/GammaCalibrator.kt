package com.example.lomanalyzer.analysis.lom

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.sqrt
import kotlin.random.Random

// TODO(Prompt-18): Replace OLS with Theil-Sen regression + MAD-based R² diagnostic.

data class GammaResult(
    val gamma: Double,
    val r2: Double,
    val ciLo: Double,
    val ciHi: Double,
    val clipped: Boolean,
    val fallback: Boolean,
    val activeAuthors: Int,
)

data class AuthorGammaInput(
    val followers: Int,
    val meanReaction: Double,
)

class GammaCalibrator(
    private val bootstrapIterations: Int = 500,
) {
    companion object {
        private const val MIN_FOLLOWERS = 100
        @Suppress("unused") private const val MIN_POSTS = 5 // used when filtering by post count
        private const val GAMMA_LO = 0.25
        private const val GAMMA_HI = 0.65
        private const val GAMMA_FALLBACK = 0.45
        private const val MIN_ACTIVE_AUTHORS = 20
        private const val MIN_R2 = 0.05
    }

    @Suppress("ReturnCount")
    fun calibrate(authors: List<AuthorGammaInput>): GammaResult {
        val active = authors.filter {
            it.followers > MIN_FOLLOWERS && it.meanReaction > 0
        }

        if (active.size < MIN_ACTIVE_AUTHORS) {
            return fallbackResult(active.size)
        }

        val xs = active.map { ln(1.0 + it.followers) }
        val ys = active.map { ln(1.0 + it.meanReaction) }

        val (beta0, beta1) = olsRegression(xs, ys)
        val r2 = computeR2(xs, ys, beta0, beta1)

        if (r2 < MIN_R2) {
            return fallbackResult(active.size)
        }

        var gammaHat = -beta1
        val clipped = gammaHat < GAMMA_LO || gammaHat > GAMMA_HI
        gammaHat = gammaHat.coerceIn(GAMMA_LO, GAMMA_HI)

        val (ciLo, ciHi) = bootstrapCI(xs, ys)

        return GammaResult(
            gamma = gammaHat,
            r2 = r2,
            ciLo = ciLo.coerceIn(GAMMA_LO, GAMMA_HI),
            ciHi = ciHi.coerceIn(GAMMA_LO, GAMMA_HI),
            clipped = clipped,
            fallback = false,
            activeAuthors = active.size,
        )
    }

    private fun fallbackResult(activeAuthors: Int) = GammaResult(
        gamma = GAMMA_FALLBACK,
        r2 = 0.0,
        ciLo = GAMMA_FALLBACK,
        ciHi = GAMMA_FALLBACK,
        clipped = false,
        fallback = true,
        activeAuthors = activeAuthors,
    )

    private fun bootstrapCI(xs: List<Double>, ys: List<Double>): Pair<Double, Double> {
        val n = xs.size
        val gammas = mutableListOf<Double>()
        val rng = Random(42)

        repeat(bootstrapIterations) {
            val indices = (0 until n).map { rng.nextInt(n) }
            val bx = indices.map { xs[it] }
            val by = indices.map { ys[it] }
            val (_, b1) = olsRegression(bx, by)
            gammas.add((-b1).coerceIn(GAMMA_LO, GAMMA_HI))
        }

        gammas.sort()
        val lo = gammas[(gammas.size * 0.05).toInt()]
        val hi = gammas[(gammas.size * 0.95).toInt().coerceAtMost(gammas.size - 1)]
        return lo to hi
    }
}

internal fun olsRegression(xs: List<Double>, ys: List<Double>): Pair<Double, Double> {
    val n = xs.size
    val xMean = xs.average()
    val yMean = ys.average()

    var ssXY = 0.0
    var ssXX = 0.0
    for (i in 0 until n) {
        val dx = xs[i] - xMean
        ssXY += dx * (ys[i] - yMean)
        ssXX += dx * dx
    }

    val beta1 = if (ssXX > 0) ssXY / ssXX else 0.0
    val beta0 = yMean - beta1 * xMean
    return beta0 to beta1
}

internal fun computeR2(xs: List<Double>, ys: List<Double>, beta0: Double, beta1: Double): Double {
    val yMean = ys.average()
    var ssRes = 0.0
    var ssTot = 0.0
    for (i in xs.indices) {
        val yPred = beta0 + beta1 * xs[i]
        ssRes += (ys[i] - yPred) * (ys[i] - yPred)
        ssTot += (ys[i] - yMean) * (ys[i] - yMean)
    }
    return if (ssTot > 0) 1.0 - ssRes / ssTot else 0.0
}
