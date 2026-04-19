package com.example.lomanalyzer.analysis.topic

data class ValidationPost(
    val id: Int,
    val text: String,
    val score: Float,
    val systemRelevant: Boolean,
    val stratum: String, // BORDERLINE_POS, BORDERLINE_NEG, HIGH_CONF, NEAR_ZERO, RANDOM
)
