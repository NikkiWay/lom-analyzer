package com.example.lomanalyzer.ui.components

data class ScatterPoint(
    val authorId: Int,
    val authorName: String,
    val iBase: Float,
    val iEvent: Float,
    val role: String,
    val confidence: Float,
    val nTopicEff: Int,
    val flags: String?,
)
