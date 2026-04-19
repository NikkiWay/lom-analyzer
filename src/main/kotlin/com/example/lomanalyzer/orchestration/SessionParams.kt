package com.example.lomanalyzer.orchestration

data class SessionParams(
    val name: String,
    val topicQuery: String,
    val region: String? = null,
    val nlpMode: String = "FULL",
    val baselineWindowDays: Int = 60,
    val currentWindowDays: Int = 30,
)
