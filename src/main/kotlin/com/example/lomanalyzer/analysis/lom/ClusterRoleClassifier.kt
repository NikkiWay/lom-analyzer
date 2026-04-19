package com.example.lomanalyzer.analysis.lom

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * GMM-based role classification per v6 §18.7.
 * 2-component EM on (I_base_hist, I_event_hist).
 * Requires n_eff >= 50 and roleMode = GMM.
 * Assigns roles by center-of-mass quadrant relative to session thresholds.
 * Confidence = posterior probability of cluster membership.
 */
data class GmmCluster(
    val meanBase: Double,
    val meanEvent: Double,
    val varBase: Double,
    val varEvent: Double,
    val weight: Double,
)

data class GmmClassificationResult(
    val authorId: Int,
    val clusterId: Int,
    val posteriorProb: Double,
    val sessionRole: SessionRole,
)

class ClusterRoleClassifier(
    @Suppress("unused") private val maxComponents: Int = 3,
    private val maxIterations: Int = 100,
    private val convergenceTol: Double = 1e-6,
) {
    companion object {
        private const val MIN_N_EFF = 50
    }

    fun isApplicable(nEff: Int): Boolean = nEff >= MIN_N_EFF

    @Suppress("LongMethod")
    fun classify(
        authorScores: List<AuthorGmmInput>,
        tauBase: Double,
        tauEvent: Double,
    ): List<GmmClassificationResult> {
        if (authorScores.size < MIN_N_EFF) return emptyList()

        val bases = authorScores.map { it.iBaseHist }
        val events = authorScores.map { it.iEventHist }

        // Fit 2 and 3 components, select by BIC
        val fit2 = fitGmm(bases, events, 2)
        val fit3 = if (authorScores.size >= 80) fitGmm(bases, events, 3) else null

        val bic2 = computeBic(bases, events, fit2)
        val bic3 = fit3?.let { computeBic(bases, events, it) } ?: Double.MAX_VALUE

        val bestFit = if (bic3 < bic2) fit3!! else fit2

        return authorScores.map { author ->
            val posteriors = bestFit.map { cluster ->
                cluster.weight * gaussianPdf(
                    author.iBaseHist, author.iEventHist, cluster,
                )
            }
            val total = posteriors.sum()
            val normalized = posteriors.map { if (total > 0) it / total else 0.0 }
            val bestCluster = normalized.indices.maxBy { normalized[it] }

            val center = bestFit[bestCluster]
            val role = RoleClassifier.classifySession(
                center.meanBase, center.meanEvent, tauBase, tauEvent,
            )

            GmmClassificationResult(
                authorId = author.authorId,
                clusterId = bestCluster,
                posteriorProb = normalized[bestCluster],
                sessionRole = role,
            )
        }
    }

    @Suppress("NestedBlockDepth")
    private fun fitGmm(
        bases: List<Double>,
        events: List<Double>,
        k: Int,
    ): List<GmmCluster> {
        val n = bases.size
        val rng = Random(42)

        // Initialize clusters
        var clusters = (0 until k).map { c ->
            val idx = rng.nextInt(n)
            GmmCluster(
                meanBase = bases[idx] + rng.nextDouble() * 0.01,
                meanEvent = events[idx] + rng.nextDouble() * 0.01,
                varBase = 0.1, varEvent = 0.1,
                weight = 1.0 / k,
            )
        }

        val resp = Array(n) { DoubleArray(k) }

        repeat(maxIterations) {
            // E-step
            for (i in 0 until n) {
                val probs = clusters.map { c ->
                    c.weight * gaussianPdf(bases[i], events[i], c)
                }
                val total = probs.sum().coerceAtLeast(1e-300)
                for (c in 0 until k) resp[i][c] = probs[c] / total
            }

            // M-step
            val newClusters = (0 until k).map { c ->
                val nk = (0 until n).sumOf { resp[it][c] }.coerceAtLeast(1e-10)
                val mB = (0 until n).sumOf { resp[it][c] * bases[it] } / nk
                val mE = (0 until n).sumOf { resp[it][c] * events[it] } / nk
                val vB = ((0 until n).sumOf {
                    resp[it][c] * (bases[it] - mB) * (bases[it] - mB)
                } / nk).coerceAtLeast(1e-6)
                val vE = ((0 until n).sumOf {
                    resp[it][c] * (events[it] - mE) * (events[it] - mE)
                } / nk).coerceAtLeast(1e-6)
                GmmCluster(mB, mE, vB, vE, nk / n)
            }

            // Check convergence
            val delta = clusters.zip(newClusters).sumOf {
                (it.first.meanBase - it.second.meanBase).let { d -> d * d } +
                    (it.first.meanEvent - it.second.meanEvent).let { d -> d * d }
            }
            clusters = newClusters
            if (delta < convergenceTol) return clusters
        }

        return clusters
    }

    private fun gaussianPdf(
        x: Double,
        y: Double,
        cluster: GmmCluster,
    ): Double {
        val dx = x - cluster.meanBase
        val dy = y - cluster.meanEvent
        val exponent = -0.5 * (dx * dx / cluster.varBase + dy * dy / cluster.varEvent)
        val norm = 2 * Math.PI * sqrt(cluster.varBase * cluster.varEvent)
        return exp(exponent) / norm.coerceAtLeast(1e-300)
    }

    private fun computeBic(
        bases: List<Double>,
        events: List<Double>,
        clusters: List<GmmCluster>,
    ): Double {
        val n = bases.size
        val k = clusters.size
        val params = k * 5 - 1 // mean_b, mean_e, var_b, var_e, weight per cluster minus 1

        var logLikelihood = 0.0
        for (i in 0 until n) {
            val prob = clusters.sumOf { c ->
                c.weight * gaussianPdf(bases[i], events[i], c)
            }.coerceAtLeast(1e-300)
            logLikelihood += ln(prob)
        }

        return -2 * logLikelihood + params * ln(n.toDouble())
    }
}

data class AuthorGmmInput(
    val authorId: Int,
    val iBaseHist: Double,
    val iEventHist: Double,
)
