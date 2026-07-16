/*
 * НАЗНАЧЕНИЕ
 * Неизменяемая (immutable) конфигурация приложения: все ключевые пути файловой
 * системы и базовые параметры (ротация логов, число итераций PBKDF2). Содержит
 * платформозависимое разрешение каталогов (Windows / macOS / Linux).
 *
 * ЧТО ВНУТРИ
 *  - data class AppConfig — поля с путями и параметрами;
 *  - companion object с фабрикой resolve() и приватными помощниками
 *    resolveAppDataDir() (каталог данных по ОС) и resolveResourcesDir()
 *    (каталог ресурсов рядом с JAR или в classpath).
 *
 * МЕТОД
 * Каталог данных выбирается по соглашениям ОС: %LOCALAPPDATA% (Windows),
 * ~/Library/Application Support (macOS), $XDG_DATA_HOME или ~/.local/share (Linux).
 *
 * СВЯЗИ
 * Создаётся в ConfigManager.initialize(); публикуется как single в Koin
 * (di/AppModule.kt) и используется для путей БД, логов, токен-хранилища,
 * session_info.json и Python-окружения.
 *
 * БИБЛИОТЕКИ
 * java.nio.file.Path/Paths — работа с путями; System.getProperty/getenv —
 * чтение системных свойств и переменных окружения.
 */
package com.example.lomanalyzer.config

import java.nio.file.Path
import java.nio.file.Paths

/**
 * Конфигурация приложения с путями файловой системы и базовыми параметрами.
 *
 * @param appDataDir корневой каталог данных приложения (зависит от ОС).
 * @param logsDir каталог логов.
 * @param resourcesDir каталог встроенных ресурсов (словари, тест-корпус).
 * @param pythonEnvPath каталог Python-окружения для NLP sidecar.
 * @param tokenVaultFile файл зашифрованного хранилища токенов VK.
 * @param sessionInfoFile файл с информацией о текущей сессии (JSON).
 * @param logRotationMaxSize максимальный размер файла лога до ротации.
 * @param logRetentionDays срок хранения логов в днях.
 * @param pbkdf2Iterations число итераций PBKDF2 для деривации ключа шифрования.
 */
data class AppConfig(
    val appDataDir: Path,
    val logsDir: Path,
    val resourcesDir: Path,
    val pythonEnvPath: Path,
    val tokenVaultFile: Path = appDataDir.resolve("token_vault.bin"),
    val sessionInfoFile: Path = appDataDir.resolve("session_info.json"),
    val logRotationMaxSize: String = "50MB",
    val logRetentionDays: Int = 7,
    val pbkdf2Iterations: Int = 100_000,
) {
    companion object {
        /**
         * Собирает AppConfig: разрешает каталог данных по ОС и производные от него пути.
         *
         * @return готовую конфигурацию приложения.
         */
        fun resolve(): AppConfig {
            // Корневой каталог данных зависит от операционной системы
            val appDataDir = resolveAppDataDir()
            return AppConfig(
                appDataDir = appDataDir,
                // Логи и Python-окружение лежат внутри каталога данных
                logsDir = appDataDir.resolve("logs"),
                resourcesDir = resolveResourcesDir(),
                pythonEnvPath = appDataDir.resolve("python"),
            )
        }

        /** Возвращает каталог данных приложения согласно соглашениям текущей ОС. */
        private fun resolveAppDataDir(): Path {
            // Определяем семейство ОС по системному свойству os.name
            val os = System.getProperty("os.name").lowercase()
            return when {
                os.contains("win") -> {
                    // Windows: %LOCALAPPDATA%, при отсутствии — домашний каталог пользователя
                    val appData = System.getenv("LOCALAPPDATA")
                        ?: System.getProperty("user.home")
                    Paths.get(appData, "LomAnalyzer")
                }
                os.contains("mac") || os.contains("darwin") -> {
                    // macOS: стандартный каталог Application Support
                    val home = System.getProperty("user.home")
                    Paths.get(home, "Library", "Application Support", "LomAnalyzer")
                }
                else -> {
                    // Linux / other — XDG_DATA_HOME or ~/.local/share
                    // Linux/прочее: переменная XDG_DATA_HOME, иначе ~/.local/share
                    val xdgData = System.getenv("XDG_DATA_HOME")
                        ?: Paths.get(System.getProperty("user.home"), ".local", "share").toString()
                    Paths.get(xdgData, "LomAnalyzer")
                }
            }
        }

        /** Возвращает каталог встроенных ресурсов: рядом с JAR или, в dev-режиме, в src/main/resources. */
        private fun resolveResourcesDir(): Path {
            // Bundled resources next to the JAR / in classpath
            // Пытаемся определить местоположение собранного кода (JAR), чтобы найти ресурсы рядом
            val codeSource = AppConfig::class.java.protectionDomain?.codeSource?.location
            return if (codeSource != null) {
                // В собранном приложении ресурсы лежат в каталоге resources рядом с JAR
                Paths.get(codeSource.toURI()).parent.resolve("resources")
            } else {
                // Fallback для запуска из IDE/исходников
                Paths.get("src", "main", "resources", "resources")
            }
        }
    }
}
