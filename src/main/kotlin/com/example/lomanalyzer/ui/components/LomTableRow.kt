package com.example.lomanalyzer.ui.components

data class LomTableRow(
    val authorId: Int,
    val authorName: String,
    val iBaseHist: Float?,
    val iBaseHistCiLo: Float?,
    val iBaseHistCiHi: Float?,
    val iBaseAbs: Float?,
    val iEventHist: Float?,
    val iEventHistCiLo: Float?,
    val iEventHistCiHi: Float?,
    val roleCombined: String?,
    val confidence: Float?,
    val nTopicEff: Int?,
    val sentiment: Float?,
    val sentimentCiLo: Float?,
    val sentimentCiHi: Float?,
    val visualActivity: Float?,
    val flags: String?,
)
