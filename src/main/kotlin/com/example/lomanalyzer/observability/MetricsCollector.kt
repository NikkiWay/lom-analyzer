/*
 * НАЗНАЧЕНИЕ
 * Сбор метрик производительности (observability): измеряет длительность этапов
 * пайплайна и агрегирует статистику (количество, сумма, среднее, максимум).
 *
 * ЧТО ВНУТРИ
 * Класс MetricsCollector: timed() (замер блока кода), record() (ручная запись
 * длительности), summary() (агрегаты по этапам), reset() и вложенный data class
 * StageSummary.
 *
 * МЕТОД
 * Тайминги хранятся по имени этапа в потокобезопасной ConcurrentHashMap, так как
 * пайплайн исполняется в корутинах/потоках. Время измеряется System.nanoTime() и
 * переводится в миллисекунды.
 *
 * СВЯЗИ
 * Регистрируется как single в Koin (di/AppModule.kt) и используется компонентами
 * пайплайна для замеров.
 *
 * БИБЛИОТЕКИ
 * java.util.concurrent.ConcurrentHashMap — потокобезопасное хранилище таймингов.
 */
package com.example.lomanalyzer.observability

import java.util.concurrent.ConcurrentHashMap

/**
 * Сборщик метрик длительности этапов с потокобезопасным накоплением замеров.
 */
class MetricsCollector {
    /** Накопленные длительности (мс) по имени этапа; потокобезопасная map. */
    private val timings = ConcurrentHashMap<String, MutableList<Long>>()

    /**
     * Замеряет длительность выполнения блока и записывает её под именем этапа.
     *
     * @param stage имя этапа (ключ метрики).
     * @param block измеряемый блок кода.
     * @return результат выполнения блока.
     */
    fun <T> timed(stage: String, block: () -> T): T {
        // Фиксируем стартовую точку в наносекундах
        val start = System.nanoTime()
        try {
            return block()
        } finally {
            // finally гарантирует запись замера даже при исключении в блоке
            val elapsed = (System.nanoTime() - start) / 1_000_000 // ms
            // Добавляем замер в список этапа (список создаётся при первом обращении)
            timings.getOrPut(stage) { mutableListOf() }.add(elapsed)
        }
    }

    /**
     * Записывает заранее измеренную длительность для этапа.
     *
     * @param stage имя этапа.
     * @param durationMs длительность в миллисекундах.
     */
    fun record(stage: String, durationMs: Long) {
        timings.getOrPut(stage) { mutableListOf() }.add(durationMs)
    }

    /**
     * Возвращает агрегированную статистику по каждому этапу.
     *
     * @return отображение «имя этапа → StageSummary».
     */
    fun summary(): Map<String, StageSummary> =
        timings.mapValues { (_, durations) ->
            StageSummary(
                count = durations.size,
                totalMs = durations.sum(),
                // Среднее считаем только при непустом списке, иначе 0.0
                avgMs = if (durations.isNotEmpty()) durations.average() else 0.0,
                maxMs = durations.maxOrNull() ?: 0,
            )
        }

    /** Очищает все накопленные тайминги. */
    fun reset() {
        timings.clear()
    }

    /**
     * Агрегированная статистика по одному этапу.
     *
     * @param count число замеров.
     * @param totalMs суммарная длительность, мс.
     * @param avgMs средняя длительность, мс.
     * @param maxMs максимальная длительность, мс.
     */
    data class StageSummary(
        val count: Int,
        val totalMs: Long,
        val avgMs: Double,
        val maxMs: Long,
    )
}
