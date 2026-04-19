package com.example.lomanalyzer.storage

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import java.nio.file.Path

class DatabaseFactory(private val dbPath: Path) {
    lateinit var database: Database
        private set

    fun initialize(): Database {
        database = Database.connect(
            url = "jdbc:sqlite:${dbPath.toAbsolutePath()}",
            driver = "org.sqlite.JDBC",
        )
        transaction(database) {
            // Use raw JDBC connection for PRAGMAs since they may return result sets
            val conn = (connection.connection as java.sql.Connection)
            conn.createStatement().use { stmt ->
                stmt.execute("PRAGMA journal_mode=WAL")
                stmt.execute("PRAGMA foreign_keys=ON")
            }
        }
        return database
    }
}
