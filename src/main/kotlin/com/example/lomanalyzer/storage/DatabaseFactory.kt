/*
 * НАЗНАЧЕНИЕ
 * Инициализация подключения к БД SQLite через Exposed ORM и настройка PRAGMA.
 * Включает режим WAL (изоляция модулей через общий файл БД — см. architecture.md)
 * и контроль внешних ключей (foreign_keys=ON).
 *
 * ЧТО ВНУТРИ
 * Класс DatabaseFactory с методом initialize(), возвращающим объект Exposed
 * Database. Хранит ссылку на подключение в свойстве database.
 *
 * МЕТОД
 * WAL (Write-Ahead Logging) повышает конкурентность чтения/записи и должен
 * устанавливаться ВНЕ транзакции — поэтому PRAGMA journal_mode=WAL выполняется
 * через отдельное прямое JDBC-подключение. PRAGMA foreign_keys=ON, напротив,
 * можно выполнять внутри транзакции Exposed.
 *
 * ФРЕЙМВОРКИ
 * Exposed ORM — Database.connect / transaction; JDBC SQLite (org.sqlite.JDBC) —
 * драйвер и прямое подключение для установки WAL. Миграции схемы — отдельно
 * (см. Migrations.kt).
 *
 * СВЯЗИ
 * Предоставляет Database для всех обращений к таблицам из storage.tables;
 * подключается через DI (Koin, di/AppModule.kt).
 */
package com.example.lomanalyzer.storage

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import java.nio.file.Path
import java.sql.DriverManager

/**
 * Фабрика подключения к БД SQLite.
 *
 * @param dbPath путь к файлу базы данных SQLite.
 */
class DatabaseFactory(private val dbPath: Path) {
    /** Инициализированное подключение Exposed; доступно только для чтения извне (private set). */
    lateinit var database: Database
        private set

    /**
     * Открывает подключение к БД, включает WAL и foreign_keys, возвращает Database.
     *
     * @return сконфигурированный объект Exposed Database.
     */
    fun initialize(): Database {
        // JDBC-URL к файлу SQLite по абсолютному пути
        val url = "jdbc:sqlite:${dbPath.toAbsolutePath()}"

        // WAL mode must be set OUTSIDE any transaction
        // Режим WAL нужно включать ВНЕ транзакции — делаем это через отдельное прямое JDBC-подключение
        DriverManager.getConnection(url).use { conn ->
            conn.createStatement().use { stmt ->
                // Включаем Write-Ahead Logging для конкурентного доступа модулей к одному файлу БД
                stmt.execute("PRAGMA journal_mode=WAL")
            }
        }

        // Основное подключение Exposed, через которое работают все таблицы
        database = Database.connect(url = url, driver = "org.sqlite.JDBC")

        // foreign_keys can be set inside a transaction
        // Контроль внешних ключей, в отличие от WAL, можно включать внутри транзакции
        transaction(database) {
            val conn = (connection.connection as java.sql.Connection)
            // Включаем проверку foreign key для целостности связей между таблицами
            conn.createStatement().use { it.execute("PRAGMA foreign_keys=ON") }
        }

        return database
    }
}
