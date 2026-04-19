package com.example.lomanalyzer.analysis.content

import com.example.lomanalyzer.nlp.NlpService

class SidecarSentimentProxy(
    private val nlpService: NlpService,
) {
    suspend fun score(text: String): SentimentResult {
        val result = nlpService.scoreSentiment(text)
        return SentimentResult(
            label = result.label.uppercase(),
            score = result.score,
            method = "MODEL",
            negationApplied = false,
        )
    }
}
