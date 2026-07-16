/*
 * НАЗНАЧЕНИЕ
 * Загрузчик встроенных (bundled) ресурсов из classpath: словарь тональности
 * (sentilex) и тестовый корпус. Дополнительно умеет считать SHA-256 ресурса для
 * проверки целостности.
 *
 * ЧТО ВНУТРИ
 * Класс ResourceLoader: loadSentilex(), loadTestCorpus(), computeSha256() и
 * приватные помощники loadResource() (чтение из classpath) и sha256() (хеш).
 *
 * СВЯЗИ
 * Регистрируется как single в Koin (di/AppModule.kt). Словарь sentilex
 * используется резервным (fallback) словарным сентиментом (analysis/content/),
 * тест-корпус — в тестах/бенчмарках. Логирование — через Logger (observability/).
 *
 * БИБЛИОТЕКИ
 * java.security.MessageDigest — вычисление SHA-256; стандартное чтение ресурсов
 * через getResourceAsStream.
 */
package com.example.lomanalyzer.config

import com.example.lomanalyzer.observability.Logger
import java.security.MessageDigest

/**
 * Loads bundled resources (sentilex dictionary, test corpus, etc.).
 *
 * Загружает встроенные ресурсы (словарь sentilex, тестовый корпус и т. п.).
 *
 * @param logger логгер для диагностики (observability/).
 */
class ResourceLoader(private val logger: Logger) {
    /** Загружает JSON словаря тональности sentilex; null, если ресурс не найден. */
    fun loadSentilex(): String? = loadResource("/resources/sentilex_base.json")

    /** Загружает JSON тестового корпуса; null, если ресурс не найден. */
    fun loadTestCorpus(): String? = loadResource("/resources/test_corpus.json")

    /**
     * Считает SHA-256 содержимого ресурса (для проверки целостности).
     *
     * @param resourcePath путь к ресурсу в classpath.
     * @return hex-строку хеша или null, если ресурс не найден.
     */
    fun computeSha256(resourcePath: String): String? {
        // Сначала читаем содержимое; если ресурса нет — хеш не считаем
        val content = loadResource(resourcePath) ?: return null
        return sha256(content)
    }

    /** Читает текстовый ресурс из classpath в строку; null, если ресурс отсутствует. */
    private fun loadResource(path: String): String? =
        ResourceLoader::class.java.getResourceAsStream(path)?.bufferedReader()?.readText()

    /** Вычисляет SHA-256 от UTF-8-байтов строки и возвращает hex-представление. */
    private fun sha256(content: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        // Каждый байт хеша форматируем как две hex-цифры и склеиваем в строку
        return digest.digest(content.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
