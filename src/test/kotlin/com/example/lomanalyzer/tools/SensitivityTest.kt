package com.example.lomanalyzer.tools

import com.example.lomanalyzer.tools.sensitivity.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SensitivityTest {

    // --- Parameter override mechanism ---

    @Test
    fun `sensitivity parameters cover 45 entries`() {
        assertTrue(
            SensitivityParameters.ALL.size >= 40,
            "Expected >= 40 params, got ${SensitivityParameters.ALL.size}",
        )
    }

    @Test
    fun `each parameter has valid low-default-high ordering`() {
        for (p in SensitivityParameters.ALL) {
            assertTrue(
                p.lowValue <= p.defaultValue && p.defaultValue <= p.highValue,
                "${p.name}: low=${p.lowValue} default=${p.defaultValue} high=${p.highValue}",
            )
        }
    }

    @Test
    fun `parameter categories cover all domains`() {
        val categories = SensitivityParameters.ALL.map { it.category }.distinct()
        assertTrue("TOPIC" in categories)
        assertTrue("GAMMA" in categories)
        assertTrue("NORM" in categories)
        assertTrue("SCORING" in categories)
        assertTrue("ANOMALY" in categories)
        assertTrue("RISK" in categories)
        assertTrue("DEDUP" in categories)
    }

    // --- Chi-square computation ---

    @Test
    fun `chi-square is 0 for identical distributions`() {
        val dist = mapOf("A" to 10, "B" to 20, "C" to 15)
        val chi = SensitivityHarness.chiSquareRoleDist(dist, dist)
        assertEquals(0.0, chi, 0.001)
    }

    @Test
    fun `chi-square is positive for different distributions`() {
        val baseline = mapOf("A" to 10, "B" to 20, "C" to 15)
        val shifted = mapOf("A" to 20, "B" to 10, "C" to 15)
        val chi = SensitivityHarness.chiSquareRoleDist(shifted, baseline)
        assertTrue(chi > 0, "chi=$chi")
    }

    // --- Impact classification ---

    @Test
    fun `high impact for large chi-square`() {
        assertEquals("HIGH", SensitivityHarness.classifyImpact(15.0, 0.05, 1))
    }

    @Test
    fun `low impact for small changes`() {
        assertEquals("LOW", SensitivityHarness.classifyImpact(1.0, 0.01, 0))
    }

    // --- Report generation ---

    @Test
    fun `report contains all sections`() {
        val report = SensitivityReport(
            baselineRoleDistribution = mapOf("TOPIC_DRIVER" to 10, "BACKGROUND" to 20),
            baselineMeanRisk = 0.25,
            baselineAnomalyCount = 5,
            runs = listOf(
                SensitivityRunResult("gamma_fallback", "LOW", 0.40,
                    mapOf("TOPIC_DRIVER" to 12, "BACKGROUND" to 18), 0.28, 6),
            ),
            highImpactParameters = listOf("volume_z_threshold"),
        )
        val md = SensitivityHarness.generateReport(report)
        assertTrue(md.contains("Sensitivity Analysis Report"))
        assertTrue(md.contains("Baseline"))
        assertTrue(md.contains("Parameter Impact Summary"))
        assertTrue(md.contains("High-Impact Parameters"))
        assertTrue(md.contains("gamma_fallback"))
    }

    // --- Test corpus schema validation ---

    @Test
    fun `corpus loader validates schema`() {
        val json = """{
            "version": "test",
            "posts": [
                {"id": 1, "from_id": 1, "published_at": 1000, "text_clean": "test",
                 "likes": 5, "reposts": 1, "comments": 1, "own_text_length": 4,
                 "has_copy_history": false, "contains_media": false,
                 "ground_truth": {"is_topic_relevant": true, "sentiment": "POSITIVE"}}
            ],
            "authors": [{"id": 1, "followers_count": 100}]
        }"""
        val corpus = TestCorpusLoader.load(json)
        val errors = TestCorpusLoader.validate(corpus)
        assertTrue(errors.isEmpty(), "errors=$errors")
    }

    @Test
    fun `corpus loader catches missing ground truth`() {
        val json = """{
            "version": "test",
            "posts": [
                {"id": 1, "from_id": 1, "published_at": 1000, "text_clean": "test",
                 "likes": 5, "reposts": 1, "comments": 1, "own_text_length": 4,
                 "has_copy_history": false, "contains_media": false}
            ],
            "authors": [{"id": 1, "followers_count": 100}]
        }"""
        val corpus = TestCorpusLoader.load(json)
        val errors = TestCorpusLoader.validate(corpus)
        assertTrue(errors.any { it.contains("missing ground_truth") })
    }

    @Test
    fun `corpus loader catches unknown author reference`() {
        val json = """{
            "version": "test",
            "posts": [
                {"id": 1, "from_id": 999, "published_at": 1000, "text_clean": "test",
                 "likes": 5, "reposts": 1, "comments": 1, "own_text_length": 4,
                 "has_copy_history": false, "contains_media": false,
                 "ground_truth": {"is_topic_relevant": true}}
            ],
            "authors": [{"id": 1, "followers_count": 100}]
        }"""
        val corpus = TestCorpusLoader.load(json)
        val errors = TestCorpusLoader.validate(corpus)
        assertTrue(errors.any { it.contains("unknown author") })
    }
}
