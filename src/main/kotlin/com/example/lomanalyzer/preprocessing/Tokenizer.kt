package com.example.lomanalyzer.preprocessing

object Tokenizer {
    // Matches: Cyrillic/Latin word sequences, or individual punctuation
    private val TOKEN_REGEX = Regex("[а-яёА-ЯЁa-zA-Z0-9]+|[^\\s]")

    fun tokenize(text: String): List<String> =
        TOKEN_REGEX.findAll(text).map { it.value }.toList()
}
