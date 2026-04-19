package com.example.lomanalyzer.storage

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import java.nio.file.Path
import java.sql.DriverManager

class DatabaseFactory(private val dbPath: Path) {
    lateinit var database: Database
        private set

    fun initialize(): Database {
        val url = "jdbc:sqlite:${dbPath.toAbsolutePath()}"

        // WAL mode must be set OUTSIDE any transaction
        DriverManager.getConnection(url).use { conn ->
            conn.createStatement().use { stmt ->
                stmt.execute("PRAGMA journal_mode=WAL")
            }
        }

        database = Database.connect(url = url, driver = "org.sqlite.JDBC")

        // foreign_keys can be set inside a transaction
        transaction(database) {
            val conn = (connection.connection as java.sql.Connection)
            conn.createStatement().use { it.execute("PRAGMA foreign_keys=ON") }
        }

        return database
    }
}
