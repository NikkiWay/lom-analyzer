/*
 * НАЗНАЧЕНИЕ
 * Интеграционные тесты UI-слоя (этап 10 — публикация в UI) и связанной логики:
 * навигация между экранами (AppNavigator), оценка качества сессии
 * (SessionQualityEvaluator) и экспорт результатов в CSV в безопасном (хэширование
 * PII) и сыром (с PII) режимах.
 *
 * ЧТО ВНУТРИ
 * Класс UiIntegrationTest:
 *  - AppNavigator: переход по основным маршрутам, переход к деталям автора и back;
 *  - AppNavigator: обход всех маршрутов NavRoute без ошибок;
 *  - SessionQualityEvaluator: подсчёт 8 индикаторов и итоговый статус PASSED;
 *  - SessionQualityEvaluator: провал при низком покрытии комментариями;
 *  - CsvExporter.exportSafe: PII (VK ID, имя) скрыты, присутствует author_hash;
 *  - CsvExporter.exportRaw: PII присутствуют в выгрузке.
 *
 * ФРЕЙМВОРКИ
 * JUnit 5 (assert*). Логика навигации основана на состоянии (StateFlow .value).
 *
 * СВЯЗИ
 * AppNavigator, NavRoute, SessionQualityEvaluator, QualityInput, QualityStatus,
 * CsvExporter, ExportRow.
 */
package com.example.lomanalyzer.ui

import com.example.lomanalyzer.analysis.quality.QualityInput
import com.example.lomanalyzer.analysis.quality.SessionQualityEvaluator
import com.example.lomanalyzer.export.CsvExporter
import com.example.lomanalyzer.export.ExportRow
import com.example.lomanalyzer.observability.Logger
import com.example.lomanalyzer.ui.navigation.AppNavigator
import com.example.lomanalyzer.ui.navigation.NavRoute
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.File

/** Интеграционные тесты навигации, оценки качества и CSV-экспорта. */
class UiIntegrationTest {

    /**
     * AppNavigator корректно меняет текущий маршрут: стартует на SETUP, переходит
     * по экранам, открывает деталь автора (с сохранением selectedAuthorId) и
     * возвращается назад через back().
     */
    @Test
    fun `AppNavigator navigates through all routes`() {
        val navigator = AppNavigator(Logger("test"))

        // Стартовый экран — настройка сессии
        assertEquals(NavRoute.SETUP, navigator.currentRoute.value)

        // Переход к экрану сбора данных
        navigator.navigate(NavRoute.COLLECTION)
        assertEquals(NavRoute.COLLECTION, navigator.currentRoute.value)

        // Переход к дашборду ЛОМ
        navigator.navigate(NavRoute.LOM_DASHBOARD)
        assertEquals(NavRoute.LOM_DASHBOARD, navigator.currentRoute.value)

        // Открытие детали конкретного автора (id=42) сохраняет выбранный id
        navigator.navigateToDetail(42)
        assertEquals(NavRoute.LOM_DETAIL, navigator.currentRoute.value)
        assertEquals(42, navigator.selectedAuthorId.value)

        // Возврат назад возвращает на дашборд
        navigator.back()
        assertEquals(NavRoute.LOM_DASHBOARD, navigator.currentRoute.value)
    }

    /**
     * Устойчивость навигации: переход на каждый маршрут перечисления NavRoute
     * проходит без ошибок и корректно выставляет текущий маршрут.
     */
    @Test
    fun `AppNavigator visits all routes without error`() {
        val navigator = AppNavigator(Logger("test"))

        // Перебираем все возможные маршруты
        for (route in NavRoute.entries) {
            navigator.navigate(route)
            assertEquals(route, navigator.currentRoute.value)
        }
    }

    /**
     * SessionQualityEvaluator при хороших входных метриках формирует 8 индикаторов,
     * все основные пройдены (allPrimaryPassed) и итоговый статус — PASSED.
     */
    @Test
    fun `SessionQualityEvaluator computes overall status`() {
        val evaluator = SessionQualityEvaluator()
        // Входные метрики качества выше всех порогов
        val result = evaluator.evaluate(QualityInput(
            collectionCompleteness = 0.98f,
            topicFilteringQuality = 0.80f,
            commentCoverage = 0.65f,
            reliableRatio = 0.60f,
            unreliableRatio = 0.10f,
            dedupEfficiency = 0.95f,
            avgCiWidth = 0.15f,
            closedAccountRatio = 0.05f,
            apiRetryRate = 0.02f,
        ))

        // Ровно 10 индикаторов качества: к восьми исходным добавлены доля
        // неизмеренной тональности и доступность семантического прохода фильтра
        assertEquals(10, result.indicators.size)
        assertTrue(result.allPrimaryPassed)
        assertEquals(com.example.lomanalyzer.core.QualityStatus.PASSED, result.overallStatus)
    }

    /**
     * Если покрытие комментариями ниже порога 0.40 (здесь 0.30), основной
     * индикатор проваливается и allPrimaryPassed становится false.
     */
    @Test
    fun `SessionQualityEvaluator fails on low comment coverage`() {
        val evaluator = SessionQualityEvaluator()
        val result = evaluator.evaluate(QualityInput(
            collectionCompleteness = 0.98f,
            topicFilteringQuality = 0.80f,
            commentCoverage = 0.30f, // below 0.40 → FAILED
            reliableRatio = 0.60f,
            unreliableRatio = 0.10f,
        ))

        // Не все основные индикаторы пройдены
        assertFalse(result.allPrimaryPassed)
    }

    /**
     * Safe-режим CSV-экспорта обезличивает данные: в файле есть колонка author_hash,
     * а исходные VK ID (123) и имя автора (Test User) отсутствуют (PII хэшируется).
     */
    @Test
    fun `CsvExporter safe mode hashes PII`() {
        val exporter = CsvExporter(Logger("test"))
        // Одна строка экспорта со всеми метриками автора
        val rows = listOf(
            ExportRow(authorVkId = 123, authorName = "Test User", aud = 4.8f, age = 0.9f, erBg = 0.02f, topVol = 5, topFocus = 0.6f, reach = 1000f, posPositive = 0.3f, posNegative = 0.5f, erTop = 0.05f, respPositive = 0.2f, respNegative = 0.4f, role = "TOPIC_ACTIVIST", sufficiency = "PRELIMINARY", topicPostCount = 5, commentCount = 20),
        )
        val tempFile = File.createTempFile("export_test", ".csv")
        try {
            // Безопасный экспорт (с хэшированием PII)
            exporter.exportSafe(rows, tempFile)
            val content = tempFile.readText()
            assertTrue(content.contains("author_hash"))
            assertFalse(content.contains("123")) // VK ID should be hashed
            assertFalse(content.contains("Test User")) // Name should not appear
        } finally {
            tempFile.delete()
        }
    }

    /**
     * Raw-режим CSV-экспорта включает PII как есть: в файле присутствуют исходный
     * VK ID (123) и имя автора (Test User) — для внутреннего использования.
     */
    @Test
    fun `CsvExporter raw mode includes PII`() {
        val exporter = CsvExporter(Logger("test"))
        val rows = listOf(
            ExportRow(authorVkId = 123, authorName = "Test User", aud = 4.8f, age = 0.9f, erBg = 0.02f, topVol = 5, topFocus = 0.6f, reach = 1000f, posPositive = 0.3f, posNegative = 0.5f, erTop = 0.05f, respPositive = 0.2f, respNegative = 0.4f, role = "TOPIC_ACTIVIST", sufficiency = "PRELIMINARY", topicPostCount = 5, commentCount = 20),
        )
        val tempFile = File.createTempFile("export_test_raw", ".csv")
        try {
            // Сырой экспорт (с PII)
            exporter.exportRaw(rows, tempFile)
            val content = tempFile.readText()
            assertTrue(content.contains("123"))
            assertTrue(content.contains("Test User"))
        } finally {
            tempFile.delete()
        }
    }
}
