package com.example.lomanalyzer.analysis.content

import kotlin.random.Random

/**
 * Dictionary-rule frame classifier per v6 §12.7.
 * 6 frames: THREAT, RESPONSIBILITY, OPPORTUNITY, DANGER, CONFLICT, SOLIDARITY.
 * Only displayed if analyst validation accuracy >= 50%.
 * NEVER used in R or roles — display only, aggregated statistics.
 */
enum class Frame(val keywords: Set<String>) {
    THREAT(setOf("угроза", "опасность", "риск", "атака", "вторжение", "разрушение")),
    RESPONSIBILITY(setOf(
        "ответственность", "вина", "виноват", "обязанность", "долг", "причина",
    )),
    OPPORTUNITY(setOf(
        "возможность", "шанс", "перспектива", "рост", "развитие", "прогресс",
    )),
    DANGER(setOf("катастрофа", "бедствие", "кризис", "авария", "ущерб", "жертва")),
    CONFLICT(setOf("конфликт", "спор", "противостояние", "борьба", "протест", "оппозиция")),
    SOLIDARITY(setOf(
        "солидарность", "единство", "поддержка", "сотрудничество", "помощь", "вместе",
    )),
}

data class FrameResult(val frame: Frame, val score: Double)

object ExperimentalFrameClassifier {
    fun classify(lemmas: List<String>): List<FrameResult> {
        val lower = lemmas.map { it.lowercase() }
        return Frame.entries.map { frame ->
            val hits = lower.count { it in frame.keywords }
            FrameResult(frame, hits.toDouble() / (lower.size + 1))
        }.filter { it.score > 0 }.sortedByDescending { it.score }
    }

    fun dominantFrame(lemmas: List<String>): Frame? =
        classify(lemmas).firstOrNull()?.frame
}

data class FrameValidationResult(
    val accuracy: Double,
    val macroF1: Double,
    val f1CiLo: Double,
    val f1CiHi: Double,
    val passedThreshold: Boolean,
    val sampleSize: Int,
)

object FrameValidator {
    private const val ACCURACY_THRESHOLD = 0.50
    private const val BOOTSTRAP_ITERATIONS = 500

    fun validate(
        predictions: List<Frame?>,
        groundTruth: List<Frame?>,
    ): FrameValidationResult {
        require(predictions.size == groundTruth.size)
        val n = predictions.size
        if (n == 0) return FrameValidationResult(0.0, 0.0, 0.0, 0.0, false, 0)

        val correct = predictions.zip(groundTruth).count { (p, g) -> p == g }
        val accuracy = correct.toDouble() / n
        val f1 = computeMacroF1(predictions, groundTruth)

        // Bootstrap CI for macro-F1
        val rng = Random(42)
        val f1s = mutableListOf<Double>()
        repeat(BOOTSTRAP_ITERATIONS) {
            val indices = (0 until n).map { rng.nextInt(n) }
            val bPred = indices.map { predictions[it] }
            val bTrue = indices.map { groundTruth[it] }
            f1s.add(computeMacroF1(bPred, bTrue))
        }
        f1s.sort()
        val ciLo = f1s[(f1s.size * 0.025).toInt()]
        val ciHi = f1s[(f1s.size * 0.975).toInt().coerceAtMost(f1s.size - 1)]

        return FrameValidationResult(
            accuracy = accuracy,
            macroF1 = f1,
            f1CiLo = ciLo,
            f1CiHi = ciHi,
            passedThreshold = accuracy >= ACCURACY_THRESHOLD,
            sampleSize = n,
        )
    }

    private fun computeMacroF1(pred: List<Frame?>, truth: List<Frame?>): Double {
        val frames = Frame.entries
        val f1s = frames.map { frame ->
            val tp = pred.zip(truth).count { (p, t) -> p == frame && t == frame }
            val fp = pred.zip(truth).count { (p, t) -> p == frame && t != frame }
            val fn = pred.zip(truth).count { (p, t) -> p != frame && t == frame }
            val prec = if (tp + fp > 0) tp.toDouble() / (tp + fp) else 0.0
            val rec = if (tp + fn > 0) tp.toDouble() / (tp + fn) else 0.0
            if (prec + rec > 0) 2 * prec * rec / (prec + rec) else 0.0
        }
        return f1s.average()
    }
}
