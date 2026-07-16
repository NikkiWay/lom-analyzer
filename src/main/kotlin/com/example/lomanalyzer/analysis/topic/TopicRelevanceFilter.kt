/*
 * НАЗНАЧЕНИЕ
 * Ядро двухпроходной тематической фильтрации (этап 6, docs/algorithm.md).
 * Преобразует результат сопоставления n-грамм (L1) и, при необходимости, оценку
 * семантической близости RuBERT (L2) в итоговое решение о тематической
 * релевантности поста и его стратум.
 *
 * ЧТО ВНУТРИ
 * - enum TopicStratum: четыре стратума итоговой классификации поста.
 * - data class TopicScoreResult: баллы L1/L2, combined, признак релевантности,
 *   метод и стратум.
 * - class TopicRelevanceFilter: вычисление L1 (computeL1) и итоговая оценка
 *   поста (score) с логикой двух проходов.
 *
 * АЛГОРИТМ (двухпроходная фильтрация L1/L2)
 * Проход 1 (L1, ключевые слова): нормированный балл по n-граммам.
 *   - L1 >= 0.50 (CONFIDENT_THRESHOLD)  -> CONFIDENT, принят без прохода 2.
 *   - 0 < L1 < 0.50                     -> пограничный, нужен проход 2.
 *   - L1 ~ 0                            -> отклонён.
 * Проход 2 (L2, RuBERT cosine, только для пограничных): cosine similarity
 *   эмбеддинга поста с эталоном.
 *   - cosine >= 0.55 (SEMANTIC_THRESHOLD) -> PASS2_CONFIRMED (принят).
 *   - cosine < 0.55                        -> DISPUTED (в список аналитику).
 * В режиме FALLBACK_ONLY (без RuBERT) пограничные посты сразу уходят в DISPUTED.
 *
 * СВЯЗИ
 * Входной NgramMatchResult даёт NgramMatcher; L2 считает SemanticScorer.
 * Управляет фильтром TopicFilterExecutor.
 *
 * БИБЛИОТЕКИ
 * kotlin.math.min — ограничение «сырого» L1 сверху; внешних зависимостей нет.
 */
package com.example.lomanalyzer.analysis.topic

import kotlin.math.min

/**
 * Classification stratum after two-pass topic filtering (diploma 2.1.1, stage 6).
 *
 * Стратум (категория) поста после двухпроходной тематической фильтрации.
 */
enum class TopicStratum {
    /** Confidently matched by keywords in pass 1 */
    CONFIDENT,
    /** Borderline after pass 1, confirmed by RuBERT cosine >= 0.55 in pass 2 */
    PASS2_CONFIRMED,
    /** Borderline after pass 1, rejected by pass 2 or no pass 2 available */
    DISPUTED,
    /** Excluded by exclusion n-grams */
    EXCLUDED,
}

/**
 * Итог тематической оценки одного поста.
 *
 * @param l1 балл первого прохода (ключевые слова) в [0..1].
 * @param l2 балл второго прохода (RuBERT cosine) в [0..1] или null, если проход 2 не выполнялся.
 * @param combined итоговый балл: для CONFIDENT — L1, для прошедших проход 2 — L2.
 * @param relevant итоговое решение о тематической релевантности.
 * @param method текстовая метка применённого метода (для отладки/логов).
 * @param stratum категория поста (см. TopicStratum).
 */
data class TopicScoreResult(
    val l1: Float,
    val l2: Float?,
    val combined: Float,
    val relevant: Boolean,
    val method: String,
    val stratum: TopicStratum,
)

/**
 * Two-pass topic filtering per diploma 2.1.1, stage 6:
 *
 * Pass 1: keyword match on lemmatized n-grams.
 *   - L1 >= CONFIDENT_THRESHOLD -> CONFIDENT (accepted without pass 2)
 *   - L1 > 0 but < CONFIDENT_THRESHOLD -> borderline, needs pass 2
 *   - L1 == 0 -> rejected
 *
 * Pass 2 (borderline only): cosine similarity on RuBERT embeddings vs reference texts.
 *   - cosine >= 0.55 -> PASS2_CONFIRMED (accepted)
 *   - cosine < 0.55 -> DISPUTED (goes to analyst review list)
 */
class TopicRelevanceFilter(
    private val semanticScorer: SemanticScorer? = null,
    private val nlpMode: String = "FALLBACK_ONLY",
) {
    companion object {
        /** Масштаб насыщения L1: «сырая» сумма попаданий нормируется делением на 3. */
        private const val L1_SCALE = 3f
        /** Вес одного попадания дополнительной (secondary) n-граммы относительно основной. */
        private const val SECONDARY_FACTOR = 0.3f
        /** L1 выше этого порога -> CONFIDENT, проход 2 не нужен (порог из диплома). */
        private const val CONFIDENT_THRESHOLD = 0.50f
        /** Порог RuBERT cosine для прохода 2 (значение по умолчанию из диплома). */
        private const val SEMANTIC_THRESHOLD = 0.55f
        /** Минимальный L1, при котором пост вообще рассматривается (ниже -> отклонён). */
        private const val MIN_L1_THRESHOLD = 0.01f
    }

    /**
     * Вычисляет нормированный балл первого прохода L1 в диапазоне [0..1].
     * Сырой балл = primaryHits + 0.3 * secondaryHits, затем ограничивается
     * сверху значением L1_SCALE и делится на него (насыщающая нормализация).
     * @return 0f, если сработала исключающая n-грамма; иначе нормированный L1.
     */
    fun computeL1(matchResult: NgramMatchResult): Float {
        // Исключённые посты сразу получают нулевой балл
        if (matchResult.excludedHit) return 0f
        // Сырой балл: основные попадания + дополнительные с понижающим весом 0.3
        val raw = matchResult.primaryHits + SECONDARY_FACTOR * matchResult.secondaryHits
        // Насыщающая нормализация: обрезаем по L1_SCALE и переводим в [0..1]
        return min(raw, L1_SCALE) / L1_SCALE
    }

    /**
     * Полная двухпроходная оценка тематической релевантности поста.
     * @param matchResult результат L1-сопоставления n-грамм.
     * @param postText исходный текст поста (нужен для эмбеддинга в проходе 2).
     * @return [TopicScoreResult] с баллами, решением и стратумом.
     */
    suspend fun score(
        matchResult: NgramMatchResult,
        postText: String,
    ): TopicScoreResult {
        // Балл первого прохода (ключевые слова)
        val l1 = computeL1(matchResult)

        // Исключён исключающими n-граммами -> стратум EXCLUDED
        if (matchResult.excludedHit) {
            return TopicScoreResult(l1 = 0f, l2 = null, combined = 0f, relevant = false,
                method = "EXCLUDED", stratum = TopicStratum.EXCLUDED)
        }

        // Совсем нет совпадений по ключевым словам -> отклонён (помечается DISPUTED)
        if (l1 < MIN_L1_THRESHOLD) {
            return TopicScoreResult(l1 = l1, l2 = null, combined = l1, relevant = false,
                method = "L1_ONLY", stratum = TopicStratum.DISPUTED)
        }

        // Проход 1: уверенное совпадение (L1 >= 0.50) -> принят без прохода 2
        if (l1 >= CONFIDENT_THRESHOLD) {
            return TopicScoreResult(l1 = l1, l2 = null, combined = l1, relevant = true,
                method = "L1_CONFIDENT", stratum = TopicStratum.CONFIDENT)
        }

        // Пограничный случай: 0 < L1 < 0.50 -> запускаем проход 2 (RuBERT), если он доступен
        if (nlpMode == "FULL" && semanticScorer != null && semanticScorer.isInitialized()) {
            // L2 = cosine similarity эмбеддинга поста с эталонным эмбеддингом темы
            val sem = semanticScorer.score(postText)
            return if (sem >= SEMANTIC_THRESHOLD) {
                // cosine >= 0.55 -> подтверждён проходом 2
                TopicScoreResult(l1 = l1, l2 = sem, combined = sem, relevant = true,
                    method = "L1_L2", stratum = TopicStratum.PASS2_CONFIRMED)
            } else {
                // cosine < 0.55 -> спорный, отправляется на ручную проверку аналитику
                TopicScoreResult(l1 = l1, l2 = sem, combined = sem, relevant = false,
                    method = "L1_L2_DISPUTED", stratum = TopicStratum.DISPUTED)
            }
        }

        // Режим fallback (RuBERT недоступен): пограничные посты уходят в DISPUTED
        return TopicScoreResult(l1 = l1, l2 = null, combined = l1, relevant = false,
            method = "L1_BORDERLINE", stratum = TopicStratum.DISPUTED)
    }
}
