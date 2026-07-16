/*
 * НАЗНАЧЕНИЕ
 * Кооперативная отмена анализа. Пользователь может прервать длительный пайплайн;
 * этот контроллер хранит флаг «отменено», который оркестратор и исполнители
 * этапов периодически проверяют и при необходимости прерывают работу, корректно
 * сохраняя checkpoint.
 *
 * ЧТО ВНУТРИ
 * Класс CancellationController с потокобезопасным флагом (cancel / reset /
 * checkCancelled / isCancelled) и собственное исключение CancellationException,
 * которое бросается при обнаружении отмены.
 *
 * МЕТОД
 * Используется AtomicBoolean — флаг устанавливается из UI-потока (PipelineLauncher.cancel),
 * а читается из coroutine пайплайна. checkCancelled — «точка проверки»: если флаг
 * взведён, бросает CancellationException, которое в PipelineOrchestrator переводит
 * сессию в статус CANCELLED.
 *
 * БИБЛИОТЕКИ
 * java.util.concurrent.atomic.AtomicBoolean — атомарный флаг для безопасного
 * доступа из разных потоков/корутин.
 */
package com.example.lomanalyzer.orchestration

import java.util.concurrent.atomic.AtomicBoolean

/** Исключение, сигнализирующее об отмене анализа пользователем. */
class CancellationException(message: String = "Analysis cancelled") : RuntimeException(message)

/** Потокобезопасный контроллер кооперативной отмены пайплайна. */
class CancellationController {
    /** Флаг «анализ отменён». Атомарный: пишется из UI, читается из coroutine. */
    private val cancelled = AtomicBoolean(false)

    /** Взводит флаг отмены (вызывается при нажатии «Отмена» в UI). */
    fun cancel() {
        cancelled.set(true)
    }

    /** Сбрасывает флаг перед запуском нового анализа. */
    fun reset() {
        cancelled.set(false)
    }

    /** Точка проверки отмены: бросает CancellationException, если флаг взведён. */
    fun checkCancelled() {
        if (cancelled.get()) {
            throw CancellationException()
        }
    }

    /** @return true, если анализ помечен на отмену. */
    fun isCancelled(): Boolean = cancelled.get()
}
