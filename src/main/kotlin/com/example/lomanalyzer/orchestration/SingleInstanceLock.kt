/*
 * НАЗНАЧЕНИЕ
 * Гарантирует, что одновременно запущена только одна копия приложения. Это важно,
 * потому что приложение работает с локальной БД SQLite (WAL) и Python sidecar —
 * параллельный запуск двух экземпляров привёл бы к конфликтам. Проверка
 * выполняется в самом начале App.main до инициализации backend.
 *
 * ЧТО ВНУТРИ
 * Класс SingleInstanceLock: acquire (захватить блокировку), release (снять),
 * вспомогательные parsePid и isProcessRunning. Блокировка реализована через
 * файл-маркер «.app_lock» в каталоге данных приложения, куда пишется PID
 * текущего процесса и временная метка.
 *
 * МЕТОД
 * При acquire: если файл блокировки уже есть — читаем из него PID и проверяем,
 * жив ли тот процесс. Если жив — другой экземпляр работает, отказ (false). Если
 * процесс мёртв — блокировка «протухшая» (stale), удаляем её и захватываем заново.
 *
 * БИБЛИОТЕКИ
 * java.nio.file (Files, Path) — работа с файлом блокировки; java.lang.ProcessHandle
 * — получение PID текущего процесса и проверка существования процесса по PID.
 */
package com.example.lomanalyzer.orchestration

import com.example.lomanalyzer.observability.Logger
import java.nio.file.Files
import java.nio.file.Path

/**
 * Файловая блокировка единственного экземпляра приложения.
 *
 * @param appDataDir каталог данных приложения, где создаётся файл блокировки.
 * @param logger логгер для сообщений о захвате/освобождении и протухших блокировках.
 */
class SingleInstanceLock(
    private val appDataDir: Path,
    private val logger: Logger,
) {
    /** Путь к файлу-маркеру блокировки в каталоге данных приложения. */
    private val lockFile: Path = appDataDir.resolve(".app_lock")

    /**
     * Пытается захватить блокировку.
     * @return true, если блокировка получена (можно запускаться); false, если
     *         другой живой экземпляр уже работает.
     */
    fun acquire(): Boolean {
        // Файл блокировки уже существует — нужно понять, живой это владелец или нет
        if (Files.exists(lockFile)) {
            val content = Files.readString(lockFile).trim()
            val existingPid = parsePid(content)
            // PID распознан и тот процесс ещё жив → реальный конкурент, отказываем
            if (existingPid != null && isProcessRunning(existingPid)) {
                logger.warn("Another instance is running (PID $existingPid)")
                return false
            }
            // Stale lock — remove it
            // Протухшая блокировка (процесс-владелец умер) — удаляем и продолжаем
            logger.info("Removing stale lock (PID $existingPid)")
            Files.deleteIfExists(lockFile)
        }
        // Каталог данных может ещё не существовать при первом запуске — создаём
        Files.createDirectories(appDataDir)
        // Записываем PID текущего процесса и метку времени как содержимое блокировки
        val pid = ProcessHandle.current().pid()
        val timestamp = System.currentTimeMillis()
        Files.writeString(lockFile, "$pid\n$timestamp")
        return true
    }

    /** Снимает блокировку, удаляя файл-маркер (безопасно, если его уже нет). */
    fun release() {
        Files.deleteIfExists(lockFile)
    }

    /** Извлекает PID из первой строки содержимого файла блокировки (или null). */
    private fun parsePid(content: String): Long? =
        content.lines().firstOrNull()?.trim()?.toLongOrNull()

    /** Проверяет, существует ли и жив ли процесс с указанным PID. */
    private fun isProcessRunning(pid: Long): Boolean =
        ProcessHandle.of(pid).map { it.isAlive }.orElse(false)
}
