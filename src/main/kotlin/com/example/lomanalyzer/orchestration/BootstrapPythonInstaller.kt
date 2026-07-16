/*
 * НАЗНАЧЕНИЕ
 * Первичная установка Python-окружения для NLP sidecar. При первом запуске
 * (режим дистрибуции BOOTSTRAP, лёгкий установщик < 100 МБ) создаёт локальный
 * venv и ставит в него NLP-зависимости (dostoevsky, pymorphy3, natasha,
 * rubert-tiny2), чтобы затем PythonServiceManager мог запустить sidecar.
 *
 * ЧТО ВНУТРИ
 * Класс BootstrapPythonInstaller: suspend install (с колбэком прогресса),
 * isInstalled (проверка наличия venv) и внутренние шаги createVenv,
 * installRequirements, verify, а также resolvePython/resolvePip (выбор путей
 * под Windows и Unix).
 *
 * АЛГОРИТМ
 * install выполняет 4 шага с отчётом прогресса: создание venv (10%), установка
 * пакетов из requirements (30%), проверка импорта pymorphy3 (90%), готово (100%).
 * Любая ошибка логируется и приводит к возврату false.
 *
 * БИБЛИОТЕКИ
 * java.lang.ProcessBuilder — запуск python3/venv/pip как внешних процессов;
 * java.nio.file — проверка путей; kotlinx.coroutines (Dispatchers.IO,
 * withContext) — блокирующие операции вынесены в IO-диспетчер.
 *
 * СВЯЗИ
 * Готовит окружение для PythonServiceManager; путь к venv (pythonEnvPath) общий.
 */
package com.example.lomanalyzer.orchestration

import com.example.lomanalyzer.observability.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path

/**
 * Bootstrap Python installer per v6 §29.2.
 * Creates a local venv and installs NLP dependencies on first launch.
 * Used when pythonDistMode = BOOTSTRAP (< 100 MB installer).
 * Устанавливает локальное Python-окружение и NLP-зависимости при первом запуске.
 *
 * @param pythonEnvPath путь к создаваемому venv.
 * @param logger логгер для сообщений об ошибках установки.
 */
class BootstrapPythonInstaller(
    private val pythonEnvPath: Path,
    private val logger: Logger,
) {
    /**
     * Создаёт venv и ставит NLP-зависимости.
     * @param onProgress колбэк прогресса (текст шага, процент 0..100).
     * @return true при успехе, false при ошибке любого шага.
     */
    suspend fun install(
        onProgress: (String, Int) -> Unit = { _, _ -> },
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            // Шаг 1: создание изолированного venv
            onProgress("Creating Python environment...", 10)
            createVenv()

            // Шаг 2: установка NLP-зависимостей из requirements.txt
            onProgress("Installing NLP packages...", 30)
            installRequirements()

            // Шаг 3: проверка, что окружение рабочее (импорт pymorphy3)
            onProgress("Verifying installation...", 90)
            verify()

            // Шаг 4: успех
            onProgress("Complete", 100)
            true
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            // Любая ошибка установки логируется и трактуется как неуспех
            logger.error("Bootstrap Python install failed", e)
            false
        }
    }

    /** Создаёт venv через системный python3; бросает ошибку при ненулевом коде выхода. */
    private fun createVenv() {
        val pb = ProcessBuilder("python3", "-m", "venv", pythonEnvPath.toString())
        // Сливаем stderr в stdout, чтобы видеть весь вывод процесса в одном потоке
        pb.redirectErrorStream(true)
        val proc = pb.start()
        proc.waitFor()
        if (proc.exitValue() != 0) {
            error("Failed to create venv")
        }
    }

    /** Ставит зависимости из requirements.txt через pip созданного venv. */
    private fun installRequirements() {
        val pip = resolvePip()
        val req = "nlp/python/requirements.txt"
        val pb = ProcessBuilder(pip, "install", "-r", req)
        pb.redirectErrorStream(true)
        val proc = pb.start()
        proc.waitFor()
    }

    /** Проверяет работоспособность окружения, импортируя pymorphy3. */
    private fun verify() {
        val python = resolvePython()
        val pb = ProcessBuilder(python, "-c", "import pymorphy3; print('OK')")
        pb.redirectErrorStream(true)
        val proc = pb.start()
        proc.waitFor()
    }

    /** @return true, если venv уже создан (есть python под Unix или Windows). */
    fun isInstalled(): Boolean = Files.exists(pythonEnvPath.resolve("bin/python")) ||
        Files.exists(pythonEnvPath.resolve("Scripts/python.exe"))

    /** Возвращает путь к python внутри venv в зависимости от ОС. */
    private fun resolvePython(): String {
        val os = System.getProperty("os.name").lowercase()
        return if (os.contains("win")) {
            pythonEnvPath.resolve("Scripts/python.exe").toString()
        } else {
            pythonEnvPath.resolve("bin/python").toString()
        }
    }

    /** Возвращает путь к pip внутри venv в зависимости от ОС. */
    private fun resolvePip(): String {
        val os = System.getProperty("os.name").lowercase()
        return if (os.contains("win")) {
            pythonEnvPath.resolve("Scripts/pip.exe").toString()
        } else {
            pythonEnvPath.resolve("bin/pip").toString()
        }
    }
}
