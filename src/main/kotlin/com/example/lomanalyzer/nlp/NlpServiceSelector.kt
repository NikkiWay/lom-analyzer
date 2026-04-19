package com.example.lomanalyzer.nlp

import com.example.lomanalyzer.observability.AppEvent
import com.example.lomanalyzer.observability.Logger
import com.example.lomanalyzer.orchestration.PythonServiceManager
import io.ktor.client.*

class NlpServiceSelector(
    private val pythonServiceManager: PythonServiceManager,
    private val localService: LocalKotlinNlpService,
    private val httpClient: HttpClient,
    private val logger: Logger,
) {
    var mode: String = "FALLBACK_ONLY"
        private set

    private var selectedService: NlpService? = null

    suspend fun initialize(): NlpService {
        val started = pythonServiceManager.start()
        if (started) {
            mode = "FULL"
            val service = PythonSidecarNlpService(
                httpClient = httpClient,
                baseUrl = pythonServiceManager.baseUrl(),
                secret = pythonServiceManager.secret,
                logger = logger,
            )
            selectedService = service
            return service
        }

        mode = "FALLBACK_ONLY"
        logger.event(AppEvent.NLP_MODE_DOWNGRADED, mapOf("reason" to "sidecar_unavailable"))
        selectedService = localService
        return localService
    }

    fun getService(): NlpService =
        selectedService ?: localService

    fun shutdown() {
        pythonServiceManager.stop()
    }
}
