/*
 * НАЗНАЧЕНИЕ
 * Расчёт индикаторов качества сессии анализа (этап 10, см. docs/algorithm.md;
 * диплом 2.2.8, таблица 2.5, Приложение Г). Оценивает, насколько данные и
 * результаты сессии достаточно полны и надёжны для интерпретации.
 *
 * ЧТО ВНУТРИ
 *  - QualityIndicator — один индикатор (имя, значение, статус, признак «основной», описание);
 *  - SessionQualityResult — набор индикаторов + общий статус сессии;
 *  - SessionQualityEvaluator — вычисляет 4 основных + технические индикаторы;
 *  - QualityInput — входные агрегаты, собираемые из разных этапов пайплайна.
 *
 * МЕТОД
 *  Каждый индикатор сравнивается с порогами (pass / borderline) -> статус
 *  PASSED/BORDERLINE/FAILED. Общая категория сессии = агрегат 4 основных
 *  индикаторов (все PASSED -> PASSED; любой FAILED -> FAILED; иначе BORDERLINE).
 *  4 основных порога: полнота сбора ≥0.95, качество фильтрации ≥0.75,
 *  покрытие комментариями ≥0.60, распределение надёжности (надёжна ≥0.50 И ненадёжна ≤0.20).
 *
 * СВЯЗИ
 *  Использует QualityStatus из core; вызывается из QualityCheckExecutor (этап 10).
 */
package com.example.lomanalyzer.analysis.quality

import com.example.lomanalyzer.core.QualityStatus

/**
 * Индикаторы качества сессии (диплом 2.2.8, таблица 2.5, Приложение Г).
 *
 * 4 основных индикатора с порогами:
 *  1. Полнота сбора данных >= 0.95
 *  2. Качество тематической фильтрации >= 0.75
 *  3. Покрытие комментариями >= 0.60
 *  4. Распределение надёжности: надёжна >= 0.50 И ненадёжна <= 0.20
 *
 * Технические диагностические индикаторы — из Приложения Г.
 * Общая категория сессии = агрегат 4 основных индикаторов.
 *
 * @param name название индикатора (на русском, для UI/отчёта).
 * @param value числовое значение индикатора.
 * @param status статус относительно порогов (PASSED/BORDERLINE/FAILED).
 * @param isPrimary true — основной индикатор (влияет на общую категорию), false — технический.
 * @param description человекочитаемое описание смысла индикатора.
 */
data class QualityIndicator(
    val name: String,
    val value: Float,
    val status: QualityStatus,
    val isPrimary: Boolean,
    val description: String,
)

/**
 * Итог оценки качества сессии.
 *
 * @param indicators все вычисленные индикаторы.
 * @param overallStatus общий статус сессии (агрегат основных индикаторов).
 */
data class SessionQualityResult(
    val indicators: List<QualityIndicator>,
    val overallStatus: QualityStatus,
) {
    /** true, если все основные индикаторы прошли (PASSED). */
    val allPrimaryPassed: Boolean
        get() = indicators.filter { it.isPrimary }.all { it.status == QualityStatus.PASSED }
}

/** Оценщик качества сессии: превращает агрегаты QualityInput в набор индикаторов. */
class SessionQualityEvaluator {

    /**
     * Вычисляет все индикаторы качества для сессии и общий статус.
     */
    fun evaluate(input: QualityInput): SessionQualityResult {
        val indicators = mutableListOf<QualityIndicator>()

        // ── 4 основных индикатора (таблица 2.5) ──

        // 1. Полнота сбора данных: порог PASSED 0.95, BORDERLINE 0.80
        indicators.add(QualityIndicator(
            name = "Полнота сбора данных",
            value = input.collectionCompleteness,
            status = thresholdStatus(input.collectionCompleteness, 0.95f, 0.80f),
            isPrimary = true,
            description = "Доля фактически собранного материала от заявленного",
        ))

        // 2. Качество тематической фильтрации: порог PASSED 0.75, BORDERLINE 0.50
        indicators.add(QualityIndicator(
            name = "Качество тематической фильтрации",
            value = input.topicFilteringQuality,
            status = thresholdStatus(input.topicFilteringQuality, 0.75f, 0.50f),
            isPrimary = true,
            description = "Доля уверенно прошедших первый проход",
        ))

        // 3. Покрытие отклика комментариями: порог PASSED 0.60, BORDERLINE 0.40
        indicators.add(QualityIndicator(
            name = "Покрытие отклика комментариями",
            value = input.commentCoverage,
            status = thresholdStatus(input.commentCoverage, 0.60f, 0.40f),
            isPrimary = true,
            description = "Доля публикаций с достаточным числом комментариев",
        ))

        // 4. Распределение надёжности оценок: комбинированное условие по двум долям
        // Норма: доля надёжных ≥0.50 И доля ненадёжных ≤0.20
        val reliabilityOk = input.reliableRatio >= 0.50f && input.unreliableRatio <= 0.20f
        // Пограничное: смягчённые пороги
        val reliabilityBorderline = input.reliableRatio >= 0.30f && input.unreliableRatio <= 0.35f
        indicators.add(QualityIndicator(
            name = "Распределение надёжности оценок",
            value = input.reliableRatio,
            status = when {
                reliabilityOk -> QualityStatus.PASSED
                reliabilityBorderline -> QualityStatus.BORDERLINE
                else -> QualityStatus.FAILED
            },
            isPrimary = true,
            description = "Надёжна ≥ 0.50 И ненадёжна ≤ 0.20",
        ))

        // ── Технические диагностические индикаторы (Приложение Г) ──

        // Эффективность дедупликации: доля уникальных публикаций; PASSED 0.90, BORDERLINE 0.70
        indicators.add(QualityIndicator(
            name = "Эффективность дедупликации",
            value = input.dedupEfficiency,
            status = thresholdStatus(input.dedupEfficiency, 0.90f, 0.70f),
            isPrimary = false,
            description = "Доля уникальных публикаций после дедупликации",
        ))

        // Средняя ширина доверительных интервалов (CI) бутстрапа: чем уже, тем надёжнее (порог-«меньше-лучше»)
        indicators.add(QualityIndicator(
            name = "Средняя ширина CI по сессии",
            value = input.avgCiWidth,
            status = when {
                input.avgCiWidth <= 0.20f -> QualityStatus.PASSED
                input.avgCiWidth <= 0.35f -> QualityStatus.BORDERLINE
                else -> QualityStatus.FAILED
            },
            isPrimary = false,
            description = "Средняя ширина 95%-доверительных интервалов",
        ))

        // Доля закрытых профилей в реестре авторов: меньше — лучше (PASSED ≤0.10, BORDERLINE ≤0.25)
        indicators.add(QualityIndicator(
            name = "Доля закрытых аккаунтов",
            value = input.closedAccountRatio,
            status = when {
                input.closedAccountRatio <= 0.10f -> QualityStatus.PASSED
                input.closedAccountRatio <= 0.25f -> QualityStatus.BORDERLINE
                else -> QualityStatus.FAILED
            },
            isPrimary = false,
            description = "Доля закрытых профилей в реестре авторов",
        ))

        // Частота повторов/ошибок запросов к VK API: меньше — лучше (PASSED ≤0.05, BORDERLINE ≤0.15)
        indicators.add(QualityIndicator(
            name = "Частота повторных попыток API",
            value = input.apiRetryRate,
            status = when {
                input.apiRetryRate <= 0.05f -> QualityStatus.PASSED
                input.apiRetryRate <= 0.15f -> QualityStatus.BORDERLINE
                else -> QualityStatus.FAILED
            },
            isPrimary = false,
            description = "Доля запросов к VK API, потребовавших повтора",
        ))

        // Доля подставленных нейтральных меток: при сбое модели текст записывается
        // как NEUTRAL, то есть значение не измерено, а подставлено. Индикатор основной:
        // при высокой доле оси позиции и отклика теряют смысл, а сессия внешне выглядит
        // завершённой. Пороги строгие (PASSED ≤0.02, BORDERLINE ≤0.10).
        indicators.add(QualityIndicator(
            name = "Доля неизмеренной тональности",
            value = input.sentimentFallbackRatio,
            status = when {
                input.sentimentFallbackRatio <= 0.02f -> QualityStatus.PASSED
                input.sentimentFallbackRatio <= 0.10f -> QualityStatus.BORDERLINE
                else -> QualityStatus.FAILED
            },
            isPrimary = true,
            description = "Доля текстов, где тональность не измерена, а подставлена нейтральной из-за сбоя модели",
        ))

        // Доступность второго прохода тематического фильтра. Без него решение по
        // пограничным постам принимается по одним ключевым словам, и вся пограничная
        // полоса отвергается — состав тематической выборки меняется, а значит меняются
        // и все оценки. Двоичный индикатор: 1 — проход работал, 0 — нет.
        indicators.add(QualityIndicator(
            name = "Семантический проход фильтра",
            value = if (input.semanticPassAvailable) 1f else 0f,
            status = if (input.semanticPassAvailable) QualityStatus.PASSED else QualityStatus.FAILED,
            isPrimary = true,
            description = "Работал ли проход 2 (RuBERT); без него пограничные посты отсеиваются по ключевым словам",
        ))

        // ── Общая категория сессии (только по основным индикаторам) ──
        val primaryStatuses = indicators.filter { it.isPrimary }.map { it.status }
        val overallStatus = when {
            // Все основные пройдены -> сессия качественная
            primaryStatuses.all { it == QualityStatus.PASSED } -> QualityStatus.PASSED
            // Хотя бы один провален -> FAILED
            primaryStatuses.any { it == QualityStatus.FAILED } -> QualityStatus.FAILED
            // Иначе пограничное качество
            else -> QualityStatus.BORDERLINE
        }

        return SessionQualityResult(indicators, overallStatus)
    }

    /**
     * Преобразует значение в статус по двум порогам (вариант «больше — лучше»).
     * @param passThreshold порог для PASSED, @param borderlineThreshold — для BORDERLINE.
     */
    private fun thresholdStatus(value: Float, passThreshold: Float, borderlineThreshold: Float): QualityStatus =
        when {
            value >= passThreshold -> QualityStatus.PASSED
            value >= borderlineThreshold -> QualityStatus.BORDERLINE
            else -> QualityStatus.FAILED
        }
}

/**
 * Входные данные для оценки качества, собираемые с разных этапов пайплайна.
 *
 * @param collectionCompleteness полнота сбора данных [0..1].
 * @param topicFilteringQuality доля уверенно прошедших тематическую фильтрацию [0..1].
 * @param commentCoverage доля публикаций с достаточным числом комментариев [0..1].
 * @param reliableRatio доля авторов с надёжными оценками [0..1].
 * @param unreliableRatio доля авторов с ненадёжными оценками [0..1].
 * @param dedupEfficiency доля уникальных публикаций после дедупликации [0..1].
 * @param avgCiWidth средняя ширина доверительных интервалов бутстрапа.
 * @param closedAccountRatio доля закрытых профилей в реестре авторов [0..1].
 * @param apiRetryRate доля запросов к VK API, потребовавших повтора [0..1].
 * @param sentimentFallbackRatio доля текстов с подставленной нейтральной меткой [0..1].
 * @param semanticPassAvailable работал ли проход 2 тематического фильтра.
 */
data class QualityInput(
    // Основные индикаторы
    val collectionCompleteness: Float = 1.0f,
    val topicFilteringQuality: Float = 1.0f,
    val commentCoverage: Float = 0.0f,
    val reliableRatio: Float = 0.0f,
    val unreliableRatio: Float = 1.0f,
    val sentimentFallbackRatio: Float = 0.0f,
    val semanticPassAvailable: Boolean = true,
    // Технические диагностические индикаторы
    val dedupEfficiency: Float = 1.0f,
    val avgCiWidth: Float = 0.0f,
    val closedAccountRatio: Float = 0.0f,
    val apiRetryRate: Float = 0.0f,
)
