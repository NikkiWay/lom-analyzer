package com.example.lomanalyzer.orchestration

import com.example.lomanalyzer.observability.AppEvent
import com.example.lomanalyzer.observability.Logger
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.delay
import java.net.ServerSocket
import java.nio.file.Path
import java.security.SecureRandom

class PythonServiceManager(
    private val pythonEnvPath: Path,
    private val httpClient: HttpClient,
    private val logger: Logger,
    private val maxRetries: Int = 3,
) {
    var port: Int = 0
        private set
    var secret: String = ""
        private set
    var process: Process? = null
        private set
    var onPermanentFailure: () -> Unit = {}

    @Suppress("TooGenericExceptionCaught")
    suspend fun start(): Boolean {
        for (attempt in 1..maxRetries) {
            try {
                port = findFreePort()
                secret = generateSecret()

                val pythonExe = resolvePythonExecutable()

                val pb = ProcessBuilder(
                    pythonExe, SIDECAR_SCRIPT,
                    "--port", port.toString(),
                    "--secret", secret,
                )
                pb.redirectErrorStream(true)
                process = pb.start()

                if (waitForHealth()) {
                    logger.event(
                        if (attempt == 1) AppEvent.PYTHON_STARTED
                        else AppEvent.PYTHON_RESTARTED,
                        mapOf("port" to port, "attempt" to attempt),
                    )
                    return true
                }

                process?.destroyForcibly()
            } catch (e: Exception) {
                logger.warn("Python sidecar start attempt $attempt failed: ${e.message}")
                process?.destroyForcibly()
            }
        }

        logger.event(AppEvent.PYTHON_FAILED_PERMANENT)
        onPermanentFailure()
        return false
    }

    fun stop() {
        process?.destroyForcibly()
        process = null
    }

    fun baseUrl(): String = "http://127.0.0.1:$port"

    private fun findFreePort(): Int {
        for (p in PORT_RANGE_START..PORT_RANGE_END) {
            try {
                ServerSocket(p).use { return p }
            } catch (_: Exception) {
                continue
            }
        }
        // Fallback: let OS pick
        return ServerSocket(0).use { it.localPort }
    }

    private fun generateSecret(): String {
        val bytes = ByteArray(SECRET_LENGTH)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun waitForHealth(): Boolean {
        val deadline = System.currentTimeMillis() + HEALTH_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            try {
                val resp = httpClient.get("http://127.0.0.1:$port/health") {
                    header("X-Auth-Token", secret)
                }
                if (resp.status == HttpStatusCode.OK) return true
            } catch (_: Exception) {
                // Not ready yet
            }
            delay(HEALTH_POLL_INTERVAL_MS)
        }
        return false
    }

    private fun resolvePythonExecutable(): String {
        val os = System.getProperty("os.name").lowercase()
        return if (os.contains("win")) {
            pythonEnvPath.resolve("Scripts/python.exe").toString()
        } else {
            pythonEnvPath.resolve("bin/python").toString()
        }
    }

    companion object {
        private const val PORT_RANGE_START = 8300
        private const val PORT_RANGE_END = 8399
        private const val HEALTH_TIMEOUT_MS = 5000L
        private const val HEALTH_POLL_INTERVAL_MS = 500L
        private const val SECRET_LENGTH = 32
        private const val SIDECAR_SCRIPT = "nlp/python/main.py"
    }
}
