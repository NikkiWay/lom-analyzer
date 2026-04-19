package com.example.lomanalyzer.config

import java.nio.file.Files

class ConfigManager {
    lateinit var config: AppConfig
        private set

    fun initialize(): AppConfig {
        config = AppConfig.resolve()
        ensureDirectories()
        return config
    }

    private fun ensureDirectories() {
        Files.createDirectories(config.appDataDir)
        Files.createDirectories(config.logsDir)
    }
}
