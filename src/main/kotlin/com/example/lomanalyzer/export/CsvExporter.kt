/*
 * НАЗНАЧЕНИЕ
 * Экспорт результатов анализа в CSV (этап 9 архитектуры — экспорт). Поддерживает
 * два режима: безопасный (exportSafe — идентификатор автора хешируется, имя не
 * выводится) и сырой (exportRaw — с VK id и именем). Реализует CSV serialization
 * вручную (без внешних библиотек).
 *
 * ЧТО ВНУТРИ
 *  - data class ExportRow — одна строка результатов по автору (11 оценок + роль,
 *    достаточность, счётчики);
 *  - class CsvExporter — exportSafe(), exportRaw() и приватный форматтер f().
 *
 * МЕТОД
 * В безопасном режиме идентификатор автора заменяется на солёный хеш (PiiHasher),
 * соль генерируется криптостойким SecureRandom. Числа форматируются с 4 знаками.
 *
 * СВЯЗИ
 * Используется SafeExporter (этот же пакет) и пайплайном. Логирует события
 * EXPORT_CSV / RAW_EXPORT_CONFIRMED через Logger (observability/). Хеширование —
 * security/PiiHasher.
 *
 * БИБЛИОТЕКИ
 * java.io.File — запись файла; java.security.SecureRandom — генерация соли;
 * PiiHasher — солёное хеширование персональных идентификаторов.
 */
package com.example.lomanalyzer.export

import com.example.lomanalyzer.observability.AppEvent
import com.example.lomanalyzer.observability.Logger
import com.example.lomanalyzer.security.PiiHasher
import java.io.File
import java.security.SecureRandom

/**
 * Одна строка экспорта — результаты по одному автору.
 * Поля соответствуют 4 осям влияния и 11 оценкам (диплом Е.4); nullable, если
 * оценка не рассчитана.
 *
 * @param authorVkId числовой VK id автора (ПДн, выводится только в raw-режиме).
 * @param authorName имя автора (ПДн, только raw-режим).
 * @param aud размер аудитории (Aud).
 * @param age возраст/стаж аккаунта (Age).
 * @param erBg вовлечённость в фоновом периоде (ER_bg).
 * @param topVol объём тематических постов (Top_vol).
 * @param topFocus тематическая сфокусированность (Top_focus).
 * @param reach охват распространения (Reach).
 * @param posPositive доля положительной позиции автора (Pos_a).
 * @param posNegative доля отрицательной позиции автора (Pos_a).
 * @param erTop вовлечённость в тематических постах (ER_top).
 * @param respPositive доля положительного отклика аудитории (Resp_a).
 * @param respNegative доля отрицательного отклика аудитории (Resp_a).
 * @param role назначенная базовая роль.
 * @param sufficiency индикатор достаточности данных.
 * @param topicPostCount число тематических постов автора.
 * @param commentCount число комментариев.
 */
data class ExportRow(
    val authorVkId: Int,
    val authorName: String,
    val aud: Float?,
    val age: Float?,
    val erBg: Float?,
    val topVol: Int?,
    val topFocus: Float?,
    val reach: Float?,
    val posPositive: Float?,
    val posNegative: Float?,
    val erTop: Float?,
    val respPositive: Float?,
    val respNegative: Float?,
    val role: String?,
    val sufficiency: String?,
    val topicPostCount: Int?,
    val commentCount: Int?,
)

/**
 * Экспортёр результатов в CSV (безопасный и сырой режимы).
 *
 * @param logger логгер для фиксации событий экспорта.
 */
class CsvExporter(private val logger: Logger) {
    /**
     * Безопасный (обезличенный) экспорт: id автора заменяется солёным хешем, имя не выводится.
     *
     * @param rows строки результатов.
     * @param outputFile файл назначения.
     */
    fun exportSafe(rows: List<ExportRow>, outputFile: File) {
        // Криптостойкая 16-байтовая соль для солёного хеширования id авторов
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        // Заголовок CSV: вместо id/имени — обезличенный author_hash
        val header = listOf(
            "author_hash", "aud", "age", "er_bg",
            "top_vol", "top_focus", "reach",
            "pos_positive", "pos_negative",
            "er_top", "resp_positive", "resp_negative",
            "role", "sufficiency", "topic_posts", "comments",
        ).joinToString(",")

        // use гарантирует закрытие writer'а даже при ошибке записи
        outputFile.bufferedWriter().use { writer ->
            writer.appendLine(header)
            for (row in rows) {
                // Хешируем VK id с солью — в файл попадает только обезличенный идентификатор
                val hash = PiiHasher.hash(row.authorVkId.toString(), salt)
                // Собираем строку: nullable-числа форматируем через f(), целые — toString или ""
                val line = listOf(
                    hash, f(row.aud), f(row.age), f(row.erBg),
                    row.topVol?.toString() ?: "", f(row.topFocus), f(row.reach),
                    f(row.posPositive), f(row.posNegative),
                    f(row.erTop), f(row.respPositive), f(row.respNegative),
                    row.role ?: "", row.sufficiency ?: "",
                    row.topicPostCount?.toString() ?: "", row.commentCount?.toString() ?: "",
                ).joinToString(",")
                writer.appendLine(line)
            }
        }

        // Фиксируем факт безопасного экспорта в логах
        logger.event(AppEvent.EXPORT_CSV, mapOf("rows" to rows.size, "file" to outputFile.name, "mode" to "safe"))
    }

    /**
     * Сырой экспорт с персональными данными (VK id и имя). Требует подтверждения на уровне SafeExporter.
     *
     * @param rows строки результатов.
     * @param outputFile файл назначения.
     */
    fun exportRaw(rows: List<ExportRow>, outputFile: File) {
        // Заголовок CSV с персональными данными: vk_id и author_name
        val header = listOf(
            "vk_id", "author_name", "aud", "age", "er_bg",
            "top_vol", "top_focus", "reach",
            "pos_positive", "pos_negative",
            "er_top", "resp_positive", "resp_negative",
            "role", "sufficiency", "topic_posts", "comments",
        ).joinToString(",")

        outputFile.bufferedWriter().use { writer ->
            writer.appendLine(header)
            for (row in rows) {
                // В сыром режиме выводим реальные id и имя автора
                val line = listOf(
                    row.authorVkId.toString(), row.authorName,
                    f(row.aud), f(row.age), f(row.erBg),
                    row.topVol?.toString() ?: "", f(row.topFocus), f(row.reach),
                    f(row.posPositive), f(row.posNegative),
                    f(row.erTop), f(row.respPositive), f(row.respNegative),
                    row.role ?: "", row.sufficiency ?: "",
                    row.topicPostCount?.toString() ?: "", row.commentCount?.toString() ?: "",
                ).joinToString(",")
                writer.appendLine(line)
            }
        }

        // Отдельное событие: подтверждённый сырой экспорт (для аудита приватности)
        logger.event(AppEvent.RAW_EXPORT_CONFIRMED, mapOf("rows" to rows.size, "file" to outputFile.name))
    }

    /** Форматирует nullable Float с 4 знаками после запятой; null → пустая строка (для CSV). */
    private fun f(v: Float?): String = v?.let { "%.4f".format(it) } ?: ""
}
