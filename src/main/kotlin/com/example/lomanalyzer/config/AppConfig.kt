package com.example.lomanalyzer.config

import java.nio.file.Path
import java.nio.file.Paths

data class AppConfig(
    val appDataDir: Path,
    val logsDir: Path,
    val resourcesDir: Path,
    val pythonEnvPath: Path,
    val tokenVaultFile: Path = appDataDir.resolve("token_vault.bin"),
    val logRotationMaxSize: String = "50MB",
    val logRetentionDays: Int = 7,
    val pbkdf2Iterations: Int = 100_000,
) {
    companion object {
        fun resolve(): AppConfig {
            val appDataDir = resolveAppDataDir()
            return AppConfig(
                appDataDir = appDataDir,
                logsDir = appDataDir.resolve("logs"),
                resourcesDir = resolveResourcesDir(),
                pythonEnvPath = appDataDir.resolve("python"),
            )
        }

        private fun resolveAppDataDir(): Path {
            val os = System.getProperty("os.name").lowercase()
            return when {
                os.contains("win") -> {
                    val appData = System.getenv("LOCALAPPDATA")
                        ?: System.getProperty("user.home")
                    Paths.get(appData, "LomAnalyzer")
                }
                os.contains("mac") || os.contains("darwin") -> {
                    val home = System.getProperty("user.home")
                    Paths.get(home, "Library", "Application Support", "LomAnalyzer")
                }
                else -> {
                    // Linux / other — XDG_DATA_HOME or ~/.local/share
                    val xdgData = System.getenv("XDG_DATA_HOME")
                        ?: Paths.get(System.getProperty("user.home"), ".local", "share").toString()
                    Paths.get(xdgData, "LomAnalyzer")
                }
            }
        }

        private fun resolveResourcesDir(): Path {
            // Bundled resources next to the JAR / in classpath
            val codeSource = AppConfig::class.java.protectionDomain?.codeSource?.location
            return if (codeSource != null) {
                Paths.get(codeSource.toURI()).parent.resolve("resources")
            } else {
                Paths.get("src", "main", "resources", "resources")
            }
        }
    }
}
