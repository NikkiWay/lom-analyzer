/*
 * НАЗНАЧЕНИЕ
 * Запуск миграций схемы БД через Flyway. Применяет SQL-скрипты V1–V10 из
 * ресурсов (db/migration) к файлу SQLite, создавая и обновляя таблицы, которые
 * декларативно описаны в пакете storage.tables.
 *
 * ЧТО ВНУТРИ
 * object Migrations с единственной функцией migrate(dbPath) — конфигурирует и
 * запускает Flyway.
 *
 * ФРЕЙМВОРКИ
 * Flyway — версионные миграции БД. Источник данных — JDBC SQLite по пути к файлу.
 *
 * СВЯЗИ
 * Вызывается при инициализации хранилища (до/совместно с DatabaseFactory).
 * Создаёт физические таблицы, соответствующие описаниям в storage.tables.
 */
package com.example.lomanalyzer.storage

import org.flywaydb.core.Flyway
import java.nio.file.Path

/** Точка запуска миграций схемы БД через Flyway. */
object Migrations {
    /**
     * Применяет все доступные миграции к базе SQLite по указанному пути.
     *
     * @param dbPath путь к файлу базы данных SQLite.
     */
    fun migrate(dbPath: Path) {
        // Конфигурируем Flyway: источник данных — SQLite по абсолютному пути (без логина/пароля)
        val flyway = Flyway.configure()
            .dataSource("jdbc:sqlite:${dbPath.toAbsolutePath()}", null, null)
            // Каталог со скриптами миграций V1–V10 внутри ресурсов classpath
            .locations("classpath:db/migration")
            // Не создавать baseline на существующей БД — ожидается чистая история миграций
            .baselineOnMigrate(false)
            // Запрещаем применение миграций вне порядка версий (строгая последовательность)
            .outOfOrder(false)
            .load()
        // Применяем недостающие миграции до актуальной версии схемы
        flyway.migrate()
    }
}
