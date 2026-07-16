/*
 * НАЗНАЧЕНИЕ
 * Менеджер конфигурации приложения: при старте формирует объект AppConfig и
 * гарантирует, что нужные рабочие каталоги (данные приложения, логи) существуют.
 *
 * ЧТО ВНУТРИ
 * Класс ConfigManager: ленивое поле config с защищённым сеттером, метод
 * initialize() (разрешает конфигурацию и создаёт каталоги) и приватный
 * ensureDirectories().
 *
 * СВЯЗИ
 * Регистрируется как single в Koin (di/AppModule.kt); результат initialize()
 * (AppConfig) тоже публикуется как отдельный single и используется всеми
 * компонентами, которым нужны пути (БД, логи, токен-хранилище, Python-окружение).
 *
 * БИБЛИОТЕКИ
 * java.nio.file.Files — создание каталогов файловой системы.
 */
package com.example.lomanalyzer.config

import java.nio.file.Files

/**
 * Менеджер конфигурации: хранит разрешённый AppConfig и создаёт рабочие каталоги.
 */
class ConfigManager {
    /** Текущая конфигурация приложения. Доступна на чтение; задаётся только внутри initialize(). */
    lateinit var config: AppConfig
        private set

    /**
     * Инициализирует конфигурацию: разрешает пути (AppConfig.resolve()) и создаёт
     * необходимые каталоги на диске.
     *
     * @return сформированный объект AppConfig.
     */
    fun initialize(): AppConfig {
        // Разрешаем платформозависимые пути (каталог данных, логи, ресурсы, Python)
        config = AppConfig.resolve()
        // Создаём каталоги на диске, чтобы дальнейшая запись не падала
        ensureDirectories()
        return config
    }

    /** Создаёт каталог данных приложения и каталог логов (если их ещё нет). */
    private fun ensureDirectories() {
        // createDirectories не бросает ошибку, если каталог уже существует
        Files.createDirectories(config.appDataDir)
        Files.createDirectories(config.logsDir)
    }
}
