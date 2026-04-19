package com.example.lomanalyzer.preprocessing

object StopWords {
    private val wordSet: Set<String> by lazy {
        val stream = StopWords::class.java.getResourceAsStream("/stopwords/ru.txt")
        stream?.bufferedReader()?.readLines()
            ?.map { it.trim().lowercase() }
            ?.filter { it.isNotBlank() && !it.startsWith("#") }
            ?.toSet()
            ?: emptySet()
    }

    fun isStopWord(word: String): Boolean =
        word.lowercase() in wordSet

    fun filter(tokens: List<String>): List<String> =
        tokens.filter { !isStopWord(it) }

    fun getAll(): Set<String> = wordSet
}
