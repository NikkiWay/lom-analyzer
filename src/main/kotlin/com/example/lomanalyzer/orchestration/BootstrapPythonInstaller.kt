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
 */
class BootstrapPythonInstaller(
    private val pythonEnvPath: Path,
    private val logger: Logger,
) {
    suspend fun install(
        onProgress: (String, Int) -> Unit = { _, _ -> },
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            onProgress("Creating Python environment...", 10)
            createVenv()

            onProgress("Installing NLP packages...", 30)
            installRequirements()

            onProgress("Verifying installation...", 90)
            verify()

            onProgress("Complete", 100)
            true
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            logger.error("Bootstrap Python install failed", e)
            false
        }
    }

    private fun createVenv() {
        val pb = ProcessBuilder("python3", "-m", "venv", pythonEnvPath.toString())
        pb.redirectErrorStream(true)
        val proc = pb.start()
        proc.waitFor()
        if (proc.exitValue() != 0) {
            error("Failed to create venv")
        }
    }

    private fun installRequirements() {
        val pip = resolvePip()
        val req = "nlp/python/requirements.txt"
        val pb = ProcessBuilder(pip, "install", "-r", req)
        pb.redirectErrorStream(true)
        val proc = pb.start()
        proc.waitFor()
    }

    private fun verify() {
        val python = resolvePython()
        val pb = ProcessBuilder(python, "-c", "import pymorphy3; print('OK')")
        pb.redirectErrorStream(true)
        val proc = pb.start()
        proc.waitFor()
    }

    fun isInstalled(): Boolean = Files.exists(pythonEnvPath.resolve("bin/python")) ||
        Files.exists(pythonEnvPath.resolve("Scripts/python.exe"))

    private fun resolvePython(): String {
        val os = System.getProperty("os.name").lowercase()
        return if (os.contains("win")) {
            pythonEnvPath.resolve("Scripts/python.exe").toString()
        } else {
            pythonEnvPath.resolve("bin/python").toString()
        }
    }

    private fun resolvePip(): String {
        val os = System.getProperty("os.name").lowercase()
        return if (os.contains("win")) {
            pythonEnvPath.resolve("Scripts/pip.exe").toString()
        } else {
            pythonEnvPath.resolve("bin/pip").toString()
        }
    }
}
