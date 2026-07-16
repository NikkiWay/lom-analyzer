/*
 * НАЗНАЧЕНИЕ
 * Тонкая обёртка над SLF4J/Logback для структурированного логирования (observability).
 * Позволяет писать как доменные события (AppEvent) со структурированными полями,
 * так и обычные сообщения уровней info/warn/error/debug.
 *
 * ЧТО ВНУТРИ
 * Класс Logger: event() (логирование доменного события с extras), info(), warn(),
 * error(), debug().
 *
 * МЕТОД
 * Структурированные поля передаются через Logstash-маркеры
 * (Markers.appendEntries) — они попадают в JSON-лог отдельными ключами, что
 * удобно для последующего анализа.
 *
 * СВЯЗИ
 * Регистрируется как single в Koin (di/AppModule.kt) и инъектируется почти во все
 * компоненты. Типы событий — в AppEvent (этот же пакет).
 *
 * БИБЛИОТЕКИ
 * org.slf4j — фасад логирования; net.logstash.logback — структурированные маркеры
 * для JSON-логов.
 */
package com.example.lomanalyzer.observability

import net.logstash.logback.marker.Markers
import org.slf4j.LoggerFactory

/**
 * Логгер приложения: структурированные события и обычные сообщения.
 *
 * @param name имя логгера (категория SLF4J), по умолчанию "LomAnalyzer".
 */
class Logger(name: String = "LomAnalyzer") {
    /** Базовый SLF4J-логгер, на который делегируются все вызовы. */
    private val log = LoggerFactory.getLogger(name)

    /**
     * Логирует доменное событие с дополнительными структурированными полями.
     *
     * @param event тип события (AppEvent).
     * @param extras дополнительные поля «ключ → значение» для структурированного лога.
     */
    fun event(event: AppEvent, extras: Map<String, Any?> = emptyMap()) {
        // Складываем тип события и дополнительные поля в Logstash-маркер
        val marker = Markers.appendEntries(
            mapOf("event_type" to event.name) + extras
        )
        // Сообщением выступает имя события; поля уходят в маркер
        log.info(marker, event.name)
    }

    /**
     * Информационное сообщение, опционально со структурированными полями.
     *
     * @param message текст сообщения.
     * @param extras дополнительные поля для лога (если непусты — пишутся через маркер).
     */
    fun info(message: String, extras: Map<String, Any?> = emptyMap()) {
        if (extras.isNotEmpty()) {
            // Есть структурированные поля — прикрепляем их маркером
            log.info(Markers.appendEntries(extras), message)
        } else {
            // Полей нет — обычное сообщение
            log.info(message)
        }
    }

    /**
     * Предупреждение, опционально с исключением.
     *
     * @param message текст предупреждения.
     * @param throwable связанное исключение (если есть).
     */
    fun warn(message: String, throwable: Throwable? = null) {
        log.warn(message, throwable)
    }

    /**
     * Ошибка, опционально с исключением.
     *
     * @param message текст ошибки.
     * @param throwable связанное исключение (если есть).
     */
    fun error(message: String, throwable: Throwable? = null) {
        log.error(message, throwable)
    }

    /**
     * Отладочное сообщение (уровень debug).
     *
     * @param message текст сообщения.
     */
    fun debug(message: String) {
        log.debug(message)
    }
}
