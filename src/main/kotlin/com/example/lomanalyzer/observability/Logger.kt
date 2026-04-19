package com.example.lomanalyzer.observability

import net.logstash.logback.marker.Markers
import org.slf4j.LoggerFactory

class Logger(name: String = "LomAnalyzer") {
    private val log = LoggerFactory.getLogger(name)

    fun event(event: AppEvent, extras: Map<String, Any?> = emptyMap()) {
        val marker = Markers.appendEntries(
            mapOf("event_type" to event.name) + extras
        )
        log.info(marker, event.name)
    }

    fun info(message: String, extras: Map<String, Any?> = emptyMap()) {
        if (extras.isNotEmpty()) {
            log.info(Markers.appendEntries(extras), message)
        } else {
            log.info(message)
        }
    }

    fun warn(message: String, throwable: Throwable? = null) {
        log.warn(message, throwable)
    }

    fun error(message: String, throwable: Throwable? = null) {
        log.error(message, throwable)
    }

    fun debug(message: String) {
        log.debug(message)
    }
}
