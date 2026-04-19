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
            connection.prepareStatement("PRAGMA journal_mode=WAL", false).executeUpdate()
            connection.prepareStatement("PRAGMA foreign_keys=ON", false).executeUpdate()
        }
        return database
    }
}
