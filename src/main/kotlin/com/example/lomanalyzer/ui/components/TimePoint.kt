package com.example.lomanalyzer.ui.components

data class TimePoint(
    val x: Int,
    val y: Float,
    val label: String = "",
    val isAnomaly: Boolean = false,
)
