/*
 * НАЗНАЧЕНИЕ
 * Обёртка над CsvExporter, реализующая принцип «приватность по умолчанию»
 * (privacy-first): сырой (raw) экспорт с персональными данными возможен только
 * при явном подтверждении пользователем, иначе экспортируется обезличенная
 * (хешированная) версия.
 *
 * ЧТО ВНУТРИ
 * Класс SafeExporter с единственным методом export(), выбирающим между
 * exportRaw() и exportSafe() у CsvExporter.
 *
 * СВЯЗИ
 * Регистрируется как single в Koin (di/AppModule.kt), делегирует работу
 * CsvExporter (этот же пакет). Соответствует политике защиты ПДн (диплом 2.2.8).
 */
package com.example.lomanalyzer.export

import java.io.File

/**
 * Wrapper enforcing privacy-first default.
 * Raw export requires explicit confirmation.
 *
 * Обёртка с приоритетом приватности по умолчанию. Сырой экспорт требует явного
 * подтверждения.
 *
 * @param csvExporter нижележащий экспортёр CSV.
 */
class SafeExporter(private val csvExporter: CsvExporter) {
    /**
     * Экспортирует строки в CSV, выбирая безопасный или сырой режим.
     *
     * @param rows строки данных авторов.
     * @param outputFile файл назначения.
     * @param rawMode запрошен ли сырой режим (с ПДн).
     * @param rawConfirmed подтверждён ли сырой режим пользователем.
     */
    fun export(
        rows: List<ExportRow>,
        outputFile: File,
        rawMode: Boolean = false,
        rawConfirmed: Boolean = false,
    ) {
        // Сырой экспорт допустим только при ОДНОВРЕМЕННОМ запросе и подтверждении
        if (rawMode && rawConfirmed) {
            csvExporter.exportRaw(rows, outputFile)
        } else {
            // По умолчанию — обезличенный (хешированный) экспорт
            csvExporter.exportSafe(rows, outputFile)
        }
    }
}
