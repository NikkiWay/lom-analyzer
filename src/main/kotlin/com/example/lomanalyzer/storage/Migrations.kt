package com.example.lomanalyzer.storage

import org.flywaydb.core.Flyway
import java.nio.file.Path

object Migrations {
    fun migrate(dbPath: Path) {
        val flyway = Flyway.configure()
            .dataSource("jdbc:sqlite:${dbPath.toAbsolutePath()}", null, null)
            .locations("classpath:db/migration")
            .baselineOnMigrate(false)
            .outOfOrder(false)
            .load()
        flyway.migrate()
    }
}
