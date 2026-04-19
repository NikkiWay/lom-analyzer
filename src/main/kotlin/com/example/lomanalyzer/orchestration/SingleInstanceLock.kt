package com.example.lomanalyzer.orchestration

import com.example.lomanalyzer.observability.Logger
import java.nio.file.Files
import java.nio.file.Path

class SingleInstanceLock(
    private val appDataDir: Path,
    private val logger: Logger,
) {
    private val lockFile: Path = appDataDir.resolve(".app_lock")

    fun acquire(): Boolean {
        if (Files.exists(lockFile)) {
            val content = Files.readString(lockFile).trim()
            val existingPid = parsePid(content)
            if (existingPid != null && isProcessRunning(existingPid)) {
                logger.warn("Another instance is running (PID $existingPid)")
                return false
            }
            // Stale lock — remove it
            logger.info("Removing stale lock (PID $existingPid)")
            Files.deleteIfExists(lockFile)
        }
        Files.createDirectories(appDataDir)
        val pid = ProcessHandle.current().pid()
        val timestamp = System.currentTimeMillis()
        Files.writeString(lockFile, "$pid\n$timestamp")
        return true
    }

    fun release() {
        Files.deleteIfExists(lockFile)
    }

    private fun parsePid(content: String): Long? =
        content.lines().firstOrNull()?.trim()?.toLongOrNull()

    private fun isProcessRunning(pid: Long): Boolean =
        ProcessHandle.of(pid).map { it.isAlive }.orElse(false)
}
