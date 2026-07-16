/*
 * НАЗНАЧЕНИЕ
 * Глобальная шина уведомлений об ошибках для UI. Любой слой приложения (VK API,
 * БД, NLP-сидекар и т. д.) может опубликовать ошибку, а UI-композаблы подписаны
 * на поток и показывают её пользователю (например, в Snackbar). Развязывает
 * источник ошибки и место её отображения.
 *
 * ЧТО ВНУТРИ
 * Класс ErrorNotifier — обёртка над SharedFlow событий ошибок с методами emit.
 * data class ErrorEvent — модель одного события (источник, код, сообщение,
 * детали) с методом format для человекочитаемого представления.
 *
 * ФРЕЙМВОРКИ
 * kotlinx.coroutines Flow (MutableSharedFlow/SharedFlow) — реактивная шина
 * событий. Регистрируется в Koin (DI) и инжектится в композаблы.
 *
 * СВЯЗИ
 * Источники вызывают emit при ошибке; подписчики UI собирают поток errors.
 */
package com.example.lomanalyzer.ui.components

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Глобальная шина уведомлений об ошибках. Любой слой может опубликовать ошибку,
 * UI-композаблы подписываются на неё и показывают Snackbar.
 */
class ErrorNotifier {
    // Внутренний горячий поток событий с буфером на 8 элементов (чтобы tryEmit не терял события)
    private val _errors = MutableSharedFlow<ErrorEvent>(extraBufferCapacity = 8)

    /** Публичный поток ошибок только для чтения; подписчики UI собирают его. */
    val errors: SharedFlow<ErrorEvent> = _errors.asSharedFlow()

    /** Публикует готовое событие ошибки в шину (без приостановки благодаря буферу). */
    fun emit(event: ErrorEvent) {
        _errors.tryEmit(event)
    }

    /** Удобная перегрузка: собирает ErrorEvent из частей и публикует его. */
    fun emit(source: String, code: Int?, message: String, detail: String? = null) {
        _errors.tryEmit(ErrorEvent(source, code, message, detail))
    }
}

/**
 * Модель одного события ошибки.
 *
 * @param source источник ошибки (например, «VK API», «БД»).
 * @param code числовой код ошибки, если есть (например, код ошибки VK API).
 * @param message основное сообщение для пользователя.
 * @param detail дополнительные детали (стек/тип исключения), опционально.
 */
data class ErrorEvent(
    val source: String,
    val code: Int? = null,
    val message: String,
    val detail: String? = null,
) {
    /** Собирает человекочитаемую строку вида «[источник] Код N: сообщение\nдетали». */
    fun format(): String = buildString {
        append("[$source] ")
        if (code != null) append("Код $code: ")
        append(message)
        if (!detail.isNullOrBlank()) append("\n$detail")
    }
}
