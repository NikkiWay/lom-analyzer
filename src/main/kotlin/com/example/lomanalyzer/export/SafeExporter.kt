package com.example.lomanalyzer.export

import java.io.File

/**
 * Wrapper enforcing privacy-first default.
 * Raw export requires explicit confirmation.
 */
class SafeExporter(private val csvExporter: CsvExporter) {
    fun export(
        rows: List<ExportRow>,
        outputFile: File,
        rawMode: Boolean = false,
        rawConfirmed: Boolean = false,
    ) {
        if (rawMode && rawConfirmed) {
            csvExporter.exportRaw(rows, outputFile)
        } else {
            csvExporter.exportSafe(rows, outputFile)
        }
    }
}
