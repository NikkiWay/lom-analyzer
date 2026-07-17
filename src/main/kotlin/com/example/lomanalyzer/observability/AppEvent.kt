/*
 * НАЗНАЧЕНИЕ
 * Перечень типов доменных событий приложения (observability). Используется при
 * структурированном логировании через Logger.event(): по полю event_type
 * события удобно фильтровать и анализировать в логах.
 *
 * ЧТО ВНУТРИ
 * enum class AppEvent — события, сгруппированные по фазам/подсистемам: жизненный
 * цикл приложения, сессии, пайплайн анализа, сбор данных, VK/авторизация,
 * NLP/Python, препроцессинг, тематическая фильтрация, дедупликация, оценки,
 * сентимент, качество, UI/экспорт, безопасность, тестирование.
 *
 * СВЯЗИ
 * Передаётся в Logger.event() (этот же пакет, observability/Logger.kt). Имена
 * событий совпадают с этапами алгоритма (docs/algorithm.md) и индикаторами
 * качества (диплом 2.2.8).
 */
package com.example.lomanalyzer.observability

/**
 * Типы доменных событий приложения для структурированного логирования.
 * Значения сгруппированы по подсистемам (см. комментарии-разделители).
 */
enum class AppEvent {
    // App lifecycle — жизненный цикл приложения
    APP_STARTED,
    APP_STOPPING,
    DB_MIGRATED,

    // Sessions — сессии анализа
    SESSION_CREATED,
    SESSION_STATUS_CHANGED,
    SESSION_FORKED,

    // Analysis pipeline — пайплайн анализа
    ANALYSIS_STARTED,
    ANALYSIS_COMPLETED,
    ANALYSIS_CANCELLED,
    CHECKPOINT_SAVED,

    // Data collection — сбор данных (этапы 2–4)
    COLLECTION_STARTED,
    COLLECTION_CHECKPOINT,
    COLLECTION_COMPLETED,
    COLLECTION_RESUMED,

    // VK / Auth — VK API и авторизация (OAuth, rate limit, backoff, токены)
    VK_OAUTH_COMPLETED,
    VK_LOGIN,
    VK_LOGOUT,
    RATE_LIMIT_HIT,
    BACKOFF_APPLIED,
    TOKEN_REFRESH_ATTEMPTED,
    TOKEN_REFRESH_SUCCESS,

    // NLP / Python — NLP-модуль и Python sidecar (выбор/деградация режима)
    PYTHON_STARTED,
    PYTHON_RESTARTED,
    PYTHON_FAILED_PERMANENT,
    PYTHON_MODELS_WARMED,
    NLP_MODE_DOWNGRADED,
    NLP_MODE_SELECTED,

    // Preprocessing — препроцессинг (этап 5: очистка, лемматизация, язык)
    PREPROCESSING_STARTED,
    PREPROCESSING_COMPLETED,
    FILTERED_OUT_LANGUAGE,

    // Topic filtering — тематическая фильтрация (этап 6, двухпроходная L1/L2)
    TOPIC_FILTER_APPLIED,
    TOPIC_SEMANTIC_PASS_DISABLED,
    TOPIC_THRESHOLD_CHANGED,
    STOPWORD_ADDED,
    VALIDATION_VOTE,

    // Deduplication — дедупликация (этап 5, near-дубликаты и оригинальность)
    DEDUP_STAGE1_COMPLETED,
    DEDUP_STAGE2_COMPLETED,
    DEDUP_GROUP_FORMED,
    ORIGINALITY_CLASSIFIED,

    // Scoring (new 4-axis model) — оценки (4 оси влияния), бутстрап, композиты, роли
    SCORING_COMPLETED,
    BOOTSTRAP_COMPLETED,
    COMPOSITE_SCORES_COMPUTED,
    ROLE_ASSIGNED,

    // Sentiment — анализ тональности
    SENTIMENT_COMPUTED,

    // Quality — индикаторы качества и гейты (этап 10)
    QUALITY_EVALUATED,
    GATES_EVALUATED,

    // UI / Export — навигация по UI и экспорт результатов
    UI_SCREEN_NAVIGATED,
    EXPORT_CSV,
    RAW_EXPORT_CONFIRMED,

    // Security — безопасность (аудит действий)
    AUDIT_ACTION,

    // Testing — тестирование (загрузка корпуса, бенчмарки)
    TEST_CORPUS_LOADED,
    BENCHMARK_COMPLETED,
}
