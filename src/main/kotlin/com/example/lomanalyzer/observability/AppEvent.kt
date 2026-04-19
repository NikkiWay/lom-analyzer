package com.example.lomanalyzer.observability

enum class AppEvent {
    APP_STARTED,
    APP_STOPPING,
    SESSION_CREATED,
    DB_MIGRATED,
    ANALYSIS_STARTED,
    ANALYSIS_COMPLETED,
    ANALYSIS_CANCELLED,
    CHECKPOINT_SAVED,
    RETENTION_HARD_DELETE,
}
