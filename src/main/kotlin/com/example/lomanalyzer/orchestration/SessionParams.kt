/*
 * НАЗНАЧЕНИЕ
 * Параметры новой сессии анализа — результат заполнения формы постановки задачи
 * (этап 1 алгоритма, SetupScreen). Передаются в SessionManager.createSession,
 * который сохраняет их в таблице analysis_sessions и далее использует на этапах
 * сбора (окна дат) и тематической фильтрации (n-граммы, эталонные тексты).
 *
 * ЧТО ВНУТРИ
 * data class SessionParams со всеми настройками: тема, ключевые слова (n-граммы),
 * эталонные тексты для L2-фильтра, регион, режим NLP/ролей, окна фона и периода,
 * путь к JSON для импорта данных вместо сбора из VK.
 *
 * СВЯЗИ
 * Создаётся в UI, потребляется SessionManager (createSession/forkSession).
 * Поля n-грамм соответствуют двухпроходной тематической фильтрации (L1 ключевые
 * слова, L2 RuBERT по referenceTexts), см. docs/algorithm.md.
 */
package com.example.lomanalyzer.orchestration

/**
 * Набор входных параметров для создания сессии анализа.
 *
 * @param name отображаемое имя сессии.
 * @param topicQuery поисковый запрос темы (для newsfeed.search, если нет сообществ).
 * @param primaryNgrams основные ключевые n-граммы (L1-фильтр, высокий приоритет).
 * @param secondaryNgrams дополнительные n-граммы (L1-фильтр).
 * @param excludedNgrams стоп-n-граммы для исключения нерелевантного.
 * @param referenceTexts эталонные тексты темы для L2-фильтра (RuBERT cosine).
 * @param region код региона для фильтрации/контекста (null — без ограничения).
 * @param nlpMode режим NLP: FULL (Python sidecar) или fallback на Kotlin.
 * @param baselineWindowDays окно фоновых данных в днях (по умолчанию 60).
 * @param currentWindowDays окно тематического периода в днях (по умолчанию 30).
 * @param importJsonPath путь к JSON для импорта данных вместо сбора из VK (или null).
 */
data class SessionParams(
    val name: String,
    val topicQuery: String,
    val primaryNgrams: List<String> = emptyList(),
    val secondaryNgrams: List<String> = emptyList(),
    val excludedNgrams: List<String> = emptyList(),
    val referenceTexts: List<String> = emptyList(),
    val region: String? = null,
    val nlpMode: String = "FULL",
    val baselineWindowDays: Int = 60,
    val currentWindowDays: Int = 30,
    val importJsonPath: String? = null,
)
