/*
 * НАЗНАЧЕНИЕ
 * Публичный контракт (интерфейс) аналитического ядра — модуля 4 архитектуры
 * (диплом 2.2.3, 2.1.3–2.1.6, 2.2.8). Объединяет этапы 7–10 алгоритма: расчёт
 * 11 количественных оценок, бутстрап доверительных интервалов, композиты и
 * квадрантную классификацию ролей, индикатор достаточности и качество сессии.
 *
 * ЧТО ВНУТРИ
 * Интерфейс AnalyticsCore с четырьмя suspend-методами (корутины): computeScores,
 * computeBootstrapIntervals, classifyRoles, evaluateQuality.
 *
 * МЕТОД
 * За контрактом стоят: 11 оценок (analysis/scoring/, Е.4), робастная статистика
 * и бутстрап (analysis/inference/, Е.1–Е.3), z-нормализация и композиты с
 * адаптивными порогами (analysis/composite/, Е.4.6), квадрантные роли
 * (analysis/roles/), достаточность (analysis/sufficiency/) и качество
 * (analysis/quality/).
 *
 * СВЯЗИ
 * Ядро читает входные данные из SQLite и пишет результаты в SQLite. Прямых
 * вызовов модулей сбора, NLP и UI нет — изоляция модулей (docs/architecture.md).
 */
package com.example.lomanalyzer.core

/**
 * Public contract for the analytics core (diploma 2.2.3, module 4).
 * Reads inputs from SQLite, writes results to SQLite.
 * No direct calls to Collection, NLP, or UI modules.
 *
 * Публичный контракт аналитического ядра (модуль 4). Читает входные данные из
 * SQLite, пишет результаты в SQLite. Без прямых вызовов модулей сбора, NLP и UI.
 *
 * Implementation spans stages 5-8 of the algorithm:
 *  - analysis/scoring/   — 11 quantitative scores
 *  - analysis/inference/  — robust statistics + bootstrap
 *  - analysis/composite/  — composite scores + adaptive thresholds
 *  - analysis/roles/      — quadrant role classification
 *  - analysis/sufficiency/ — data sufficiency indicator
 *  - analysis/quality/    — session quality indicators
 */
interface AnalyticsCore {
    /**
     * Этап 7 (алгоритм): расчёт 11 оценок для всех авторов сессии.
     *
     * Stage 7 (algorithm): compute 11 scores for all authors in session
     *
     * @param sessionId идентификатор сессии анализа.
     */
    suspend fun computeScores(sessionId: Int)

    /**
     * Этап 8 (алгоритм): бутстрап доверительных интервалов (CI) для оценок,
     * основанных на выборках.
     *
     * Stage 8 (algorithm): bootstrap CIs for sample-based scores
     *
     * @param sessionId идентификатор сессии анализа.
     */
    suspend fun computeBootstrapIntervals(sessionId: Int)

    /**
     * Этап 9 (алгоритм): композитные оценки + классификация ролей.
     *
     * Stage 9 (algorithm): composite scores + role classification
     *
     * @param sessionId идентификатор сессии анализа.
     */
    suspend fun classifyRoles(sessionId: Int)

    /**
     * Этап 10 (алгоритм): индикатор достаточности данных + качество сессии.
     *
     * Stage 10 (algorithm): sufficiency indicator + session quality
     *
     * @param sessionId идентификатор сессии анализа.
     */
    suspend fun evaluateQuality(sessionId: Int)
}
