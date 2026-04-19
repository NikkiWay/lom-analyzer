package com.example.lomanalyzer.analysis.lom

import kotlin.math.ln
import kotlin.random.Random

/**
 * Gamma calibration per v6 §14.1.2.
 * Primary: Theil-Sen regression with MAD-based R².
 * Fallback to OLS if |A*| < 20.
 * Clip gamma into [0.25, 0.65]; fallback to 0.45 if R²_MAD < 0.05.
 * Bootstrap 1000 for CI.
 */
data class GammaResult(
    val gamma: Double,
    val r2: Double,
    val ciLo: Double,
    val ciHi: Double,
    val clipped: Boolean,
    val fallback: Boolean,
    val activeAuthors: Int,
    val method: String = "THEIL_SEN",
)

data class AuthorGammaInput(
    val followers: Int,
    val meanReaction: Double,
)

class GammaCalibrator(
    private val bootstrapIterations: Int = 1000,
) {
    companion object {
        private const val MIN_FOLLOWERS = 100
        private const val GAMMA_LO = 0.25
        private const val GAMMA_HI = 0.65
        private const val GAMMA_FALLBACK = 0.45
        private const val MIN_ACTIVE_FOR_THEIL_SEN = 20
        private const val MIN_R2 = 0.05
    }

    @Suppress("ReturnCount")
    fun calibrate(authors: List<AuthorGammaInput>): GammaResult {
        val active = authors.filter {
            it.followers > MIN_FOLLOWERS && it.meanReaction > 0
        }

        if (active.isEmpty()) return fallbackResult(0)

        val xs = active.map { ln(1.0 + it.followers) }
        val ys = active.map { ln(1.0 + it.meanReaction) }

        // Use Theil-Sen as primary; OLS as fallback for small samples
        val useTheilSen = active.size >= MIN_ACTIVE_FOR_THEIL_SEN
        val reg = if (useTheilSen) {
            RobustRegression.theilSen(xs, ys)
        } else {
            val (b0, b1) = olsRegression(xs, ys)
            RegressionResult(b1, b0)
        }

        val r2 = if (useTheilSen) {
            RobustRegression.madR2(xs, ys, reg.slope, reg.intercept)
        } else {
            computeR2(xs, ys, reg.intercept, reg.slope)
        }

        if (r2 < MIN_R2 && active.size < MIN_ACTIVE_FOR_THEIL_SEN) {
            return fallbackResult(active.size)
        }

        var gammaHat = -reg.slope
        val clipped = gammaHat < GAMMA_LO || gammaHat > GAMMA_HI
        gammaHat = gammaHat.coerceIn(GAMMA_LO, GAMMA_HI)

        val (ciLo, ciHi) = if (useTheilSen) {
            val (lo, hi) = RobustRegression.bootstrapTheilSenCI(
                xs, ys, bootstrapIterations,
            )
            (-hi).coerceIn(GAMMA_LO, GAMMA_HI) to (-lo).coerceIn(GAMMA_LO, GAMMA_HI)
        } else {
            bootstrapOlsCI(xs, ys)
        }

        return GammaResult(
            gamma = gammaHat,
            r2 = r2,
            ciLo = ciLo,
            ciHi = ciHi,
            clipped = clipped,
            fallback = false,
            activeAuthors = active.size,
            method = if (useTheilSen) "THEIL_SEN" else "OLS",
        )
    }

    private fun fallbackResult(activeAuthors: Int) = GammaResult(
        gamma = GAMMA_FALLBACK, r2 = 0.0,
        ciLo = GAMMA_FALLBACK, ciHi = GAMMA_FALLBACK,
        clipped = false, fallback = true,
        activeAuthors = activeAuthors, method = "FALLBACK",
    )

    private fun bootstrapOlsCI(
        xs: List<Double>,
        ys: List<Double>,
    ): Pair<Double, Double> {
        val n = xs.size
        val gammas = mutableListOf<Double>()
        val rng = Random(42)
        repeat(bootstrapIterations) {
            val indices = (0 until n).map { rng.nextInt(n) }
            val (_, b1) = olsRegression(indices.map { xs[it] }, indices.map { ys[it] })
            gammas.add((-b1).coerceIn(GAMMA_LO, GAMMA_HI))
        }
        gammas.sort()
        return gammas[(gammas.size * 0.05).toInt()] to
            gammas[(gammas.size * 0.95).toInt().coerceAtMost(gammas.size - 1)]
    }
}

internal fun olsRegression(xs: List<Double>, ys: List<Double>): Pair<Double, Double> {
    val xMean = xs.average()
    val yMean = ys.average()
    var ssXY = 0.0; var ssXX = 0.0
    for (i in xs.indices) {
        val dx = xs[i] - xMean
        ssXY += dx * (ys[i] - yMean)
        ssXX += dx * dx
    }
    val beta1 = if (ssXX > 0) ssXY / ssXX else 0.0
    val beta0 = yMean - beta1 * xMean
    return beta0 to beta1
}

internal fun computeR2(
    xs: List<Double>,
    ys: List<Double>,
    beta0: Double,
    beta1: Double,
): Double {
    val yMean = ys.average()
    var ssRes = 0.0; var ssTot = 0.0
    for (i in xs.indices) {
        val yPred = beta0 + beta1 * xs[i]
        ssRes += (ys[i] - yPred) * (ys[i] - yPred)
        ssTot += (ys[i] - yMean) * (ys[i] - yMean)
    }
    return if (ssTot > 0) 1.0 - ssRes / ssTot else 0.0
}
