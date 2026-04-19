package com.example.lomanalyzer.orchestration

enum class SessionStatus {
    CREATED,
    COLLECTING,
    ANALYZING,
    PAUSED_PENDING_RECOVERY,
    COMPLETED,
    INCOMPLETE,
    CANCELLED,
    FAILED,
}
