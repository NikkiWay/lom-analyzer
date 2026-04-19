package com.example.lomanalyzer.export

import com.example.lomanalyzer.observability.AppEvent
import com.example.lomanalyzer.observability.Logger
import com.example.lomanalyzer.security.PiiHasher
import java.io.File
import java.security.SecureRandom

data class ExportRow(
    val authorVkId: Int,
    val authorName: String,
    val iBaseHist: Float?,
    val iEventHist: Float?,
    val roleCombined: String?,
    val sentiment: Float?,
)

class CsvExporter(private val logger: Logger) {
    fun exportSafe(rows: List<ExportRow>, outputFile: File) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val header = "author_hash,i_base_hist,i_event_hist,role,sentiment"

        outputFile.bufferedWriter().use { writer ->
            writer.appendLine(header)
            for (row in rows) {
                val hash = PiiHasher.hash(row.authorVkId.toString(), salt)
                val line = listOf(hash, f(row.iBaseHist), f(row.iEventHist),
                    row.roleCombined ?: "", f(row.sentiment)).joinToString(",")
                writer.appendLine(line)
            }
        }

        logger.event(AppEvent.EXPORT_CSV, mapOf(
            "rows" to rows.size,
            "file" to outputFile.name,
            "mode" to "safe",
        ))
    }

    fun exportRaw(rows: List<ExportRow>, outputFile: File) {
        val header = "vk_id,author_name,i_base_hist,i_event_hist,role,sentiment"

        outputFile.bufferedWriter().use { writer ->
            writer.appendLine(header)
            for (row in rows) {
                val line = listOf(row.authorVkId, row.authorName,
                    f(row.iBaseHist), f(row.iEventHist),
                    row.roleCombined ?: "", f(row.sentiment)).joinToString(",")
                writer.appendLine(line)
            }
        }

        logger.event(AppEvent.RAW_EXPORT_CONFIRMED, mapOf(
            "rows" to rows.size,
            "file" to outputFile.name,
        ))
    }

    private fun f(v: Float?): String = v?.let { "%.4f".format(it) } ?: ""
}
