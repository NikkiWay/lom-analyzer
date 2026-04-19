package com.example.lomanalyzer.ui

import com.example.lomanalyzer.analysis.quality.SessionQualityEvaluator
import com.example.lomanalyzer.export.CsvExporter
import com.example.lomanalyzer.export.ExportRow
import com.example.lomanalyzer.observability.Logger
import com.example.lomanalyzer.ui.navigation.AppNavigator
import com.example.lomanalyzer.ui.navigation.NavRoute
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.File

class UiIntegrationTest {

    @Test
    fun `AppNavigator navigates through all routes`() {
        val navigator = AppNavigator(Logger("test"))

        assertEquals(NavRoute.SETUP, navigator.currentRoute.value)

        navigator.navigate(NavRoute.COLLECTION)
        assertEquals(NavRoute.COLLECTION, navigator.currentRoute.value)

        navigator.navigate(NavRoute.LOM_DASHBOARD)
        assertEquals(NavRoute.LOM_DASHBOARD, navigator.currentRoute.value)

        navigator.navigateToDetail(42)
        assertEquals(NavRoute.LOM_DETAIL, navigator.currentRoute.value)
        assertEquals(42, navigator.selectedAuthorId.value)

        navigator.back()
        assertEquals(NavRoute.LOM_DASHBOARD, navigator.currentRoute.value)
    }

    @Test
    fun `AppNavigator visits all routes without error`() {
        val navigator = AppNavigator(Logger("test"))

        for (route in NavRoute.entries) {
            navigator.navigate(route)
            assertEquals(route, navigator.currentRoute.value)
        }
    }

    @Test
    fun `SessionQualityEvaluator computes overall score`() {
        val evaluator = SessionQualityEvaluator()
        val result = evaluator.evaluate(
            coverageRatio = 0.85f,
            topicPrecision = 0.90f,
            topicRecall = 0.80f,
            dedupRatio = 0.95f,
            gammaR2 = 0.30f,
            cvIqrMax = 0.20f,
            bootstrapWidthMean = 0.15f,
            confidenceAbove25Pct = 0.75f,
            referenceFreshness = 0.90f,
        )

        assertTrue(result.overallScore > 0)
        assertEquals(9, result.components.size)
        assertTrue(result.allGatesPassed)
    }

    @Test
    fun `SessionQualityEvaluator fails gate on high CV_IQR`() {
        val evaluator = SessionQualityEvaluator()
        val result = evaluator.evaluate(
            coverageRatio = 0.85f,
            topicPrecision = 0.90f,
            topicRecall = 0.80f,
            dedupRatio = 0.95f,
            gammaR2 = 0.30f,
            cvIqrMax = 0.60f,  // > 0.5 → gate fails
            bootstrapWidthMean = 0.15f,
            confidenceAbove25Pct = 0.75f,
            referenceFreshness = 0.90f,
        )

        assertFalse(result.allGatesPassed)
    }

    @Test
    fun `CsvExporter safe mode hashes PII`() {
        val exporter = CsvExporter(Logger("test"))
        val rows = listOf(
            ExportRow(123, "Test User", 0.65f, 0.45f, "TOPIC_DRIVER", -0.2f),
        )
        val tempFile = File.createTempFile("export_test", ".csv")
        try {
            exporter.exportSafe(rows, tempFile)
            val content = tempFile.readText()
            assertTrue(content.contains("author_hash"))
            assertFalse(content.contains("123")) // VK ID should be hashed
            assertFalse(content.contains("Test User")) // Name should not appear
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun `CsvExporter raw mode includes PII`() {
        val exporter = CsvExporter(Logger("test"))
        val rows = listOf(
            ExportRow(123, "Test User", 0.65f, 0.45f, "TOPIC_DRIVER", -0.2f),
        )
        val tempFile = File.createTempFile("export_test_raw", ".csv")
        try {
            exporter.exportRaw(rows, tempFile)
            val content = tempFile.readText()
            assertTrue(content.contains("123"))
            assertTrue(content.contains("Test User"))
        } finally {
            tempFile.delete()
        }
    }
}
