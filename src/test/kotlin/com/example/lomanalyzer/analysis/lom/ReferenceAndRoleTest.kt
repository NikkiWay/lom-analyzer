package com.example.lomanalyzer.analysis.lom

import com.example.lomanalyzer.config.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ReferenceAndRoleTest {

    private val testRef = ReferenceBase(
        version = "1.0.0",
        gammaUsedInCollection = 0.45,
        rawQuantileStatistics = RawQuantileStatistics(
            lnF = QuantileStats(q50 = 7.8, iqr = 3.3),
            lnRBar = QuantileStats(q50 = 3.4, iqr = 3.4),
        ),
        computedStatsAtGammaRef = ComputedStatsAtGamma(
            eRawAtGamma = QuantileStats(q50 = 0.1, iqr = 2.6),
            iBaseAtGamma = IBasePercentiles(p50 = 0.53, p75 = 0.78),
        ),
        iBaseThresholds = IBaseThresholds(
            tauBaseP75 = 0.78,
            fP75 = 13000,
        ),
    )

    // --- ReferenceCalibrator OK-branch ---

    @Test
    fun `OK branch produces valid I_base_abs`() {
        val cal = ReferenceCalibrator()
        val iBaseAbs = cal.calibrate(
            followers = 5000,
            eRawSession = 1.0,
            gammaDivergenceFlag = "OK",
            ref = testRef,
        )
        assertTrue(iBaseAbs in 0.0..1.0, "iBaseAbs=$iBaseAbs")
    }

    @Test
    fun `high followers produce high I_base_abs`() {
        val cal = ReferenceCalibrator()
        val low = cal.calibrate(100, 0.0, "OK", testRef)
        val high = cal.calibrate(50000, 2.0, "OK", testRef)
        assertTrue(high > low, "high=$high should be > low=$low")
    }

    @Test
    fun `AUDIENCE_ONLY uses only A component`() {
        val cal = ReferenceCalibrator()
        // Same followers, different E_raw → should not matter
        val a = cal.calibrate(5000, -5.0, "AUDIENCE_ONLY_REFERENCE", testRef)
        val b = cal.calibrate(5000, 5.0, "AUDIENCE_ONLY_REFERENCE", testRef)
        assertEquals(a, b, 0.001, "E_raw should be ignored")
    }

    @Test
    fun `OK branch considers both A and E`() {
        val cal = ReferenceCalibrator()
        val lowE = cal.calibrate(5000, -3.0, "OK", testRef)
        val highE = cal.calibrate(5000, 3.0, "OK", testRef)
        assertTrue(highE > lowE, "higher E should increase score")
    }

    // --- RoleCombinator: 8 explicit matrix cases ---

    @Test
    fun `AUTHORITATIVE_LOM x HIGH_ABS = CONFIRMED`() {
        val r = RoleCombinator.combine(SessionRole.AUTHORITATIVE_LOM, ReferenceQuadrant.HIGH_ABS_BASE)
        assertEquals(CombinedRole.AUTHORITATIVE_LOM_CONFIRMED, r)
    }

    @Test
    fun `AUTHORITATIVE_LOM x LOW_ABS = LOCAL`() {
        val r = RoleCombinator.combine(SessionRole.AUTHORITATIVE_LOM, ReferenceQuadrant.LOW_ABS_BASE)
        assertEquals(CombinedRole.AUTHORITATIVE_LOM_LOCAL, r)
    }

    @Test
    fun `SLEEPING_GIANT x HIGH_ABS = CONFIRMED`() {
        val r = RoleCombinator.combine(SessionRole.SLEEPING_GIANT, ReferenceQuadrant.HIGH_ABS_BASE)
        assertEquals(CombinedRole.SLEEPING_GIANT_CONFIRMED, r)
    }

    @Test
    fun `SLEEPING_GIANT x LOW_ABS = LOCAL`() {
        val r = RoleCombinator.combine(SessionRole.SLEEPING_GIANT, ReferenceQuadrant.LOW_ABS_BASE)
        assertEquals(CombinedRole.SLEEPING_GIANT_LOCAL, r)
    }

    @Test
    fun `TOPIC_DRIVER x HIGH_ABS = WITH_BASE`() {
        val r = RoleCombinator.combine(SessionRole.TOPIC_DRIVER, ReferenceQuadrant.HIGH_ABS_BASE)
        assertEquals(CombinedRole.TOPIC_DRIVER_WITH_BASE, r)
    }

    @Test
    fun `TOPIC_DRIVER x LOW_ABS = TOPIC_DRIVER`() {
        val r = RoleCombinator.combine(SessionRole.TOPIC_DRIVER, ReferenceQuadrant.LOW_ABS_BASE)
        assertEquals(CombinedRole.TOPIC_DRIVER, r)
    }

    @Test
    fun `BACKGROUND x HIGH_ABS = LARGE`() {
        val r = RoleCombinator.combine(SessionRole.BACKGROUND, ReferenceQuadrant.HIGH_ABS_BASE)
        assertEquals(CombinedRole.BACKGROUND_LARGE, r)
    }

    @Test
    fun `BACKGROUND x LOW_ABS = BACKGROUND`() {
        val r = RoleCombinator.combine(SessionRole.BACKGROUND, ReferenceQuadrant.LOW_ABS_BASE)
        assertEquals(CombinedRole.BACKGROUND, r)
    }

    // --- BASELINE_UNKNOWN ---

    @Test
    fun `BASELINE_UNKNOWN overrides matrix`() {
        val r = RoleCombinator.combine(
            SessionRole.AUTHORITATIVE_LOM,
            ReferenceQuadrant.HIGH_ABS_BASE,
            isBaselineUnknown = true,
        )
        assertEquals(CombinedRole.BASELINE_UNKNOWN, r)
    }

    @Test
    fun `BASELINE_UNKNOWN confidence is 0`() {
        val conf = RoleClassifier.computeConfidence(
            iBaseHist = 0.8,
            iEventHist = 0.7,
            tauBase = 0.5,
            tauEvent = 0.5,
            qBase = 0.2,
            qEvent = 0.2,
            nEff = 100,
            isBaselineUnknown = true,
        )
        assertEquals(0.0, conf)
    }

    // --- RoleClassifier session quadrant ---

    @Test
    fun `session quadrant assignment`() {
        assertEquals(
            SessionRole.AUTHORITATIVE_LOM,
            RoleClassifier.classifySession(0.8, 0.7, 0.5, 0.5),
        )
        assertEquals(
            SessionRole.SLEEPING_GIANT,
            RoleClassifier.classifySession(0.8, 0.3, 0.5, 0.5),
        )
        assertEquals(
            SessionRole.TOPIC_DRIVER,
            RoleClassifier.classifySession(0.3, 0.7, 0.5, 0.5),
        )
        assertEquals(
            SessionRole.BACKGROUND,
            RoleClassifier.classifySession(0.3, 0.3, 0.5, 0.5),
        )
    }

    // --- Confidence with sqrt(n_eff) penalty ---

    @Test
    fun `confidence increases with n_eff`() {
        val confSmall = RoleClassifier.computeConfidence(
            0.8, 0.7, 0.5, 0.5, 0.2, 0.2, nEff = 5,
        )
        val confLarge = RoleClassifier.computeConfidence(
            0.8, 0.7, 0.5, 0.5, 0.2, 0.2, nEff = 100,
        )
        assertTrue(confLarge > confSmall, "large=$confLarge > small=$confSmall")
    }

    @Test
    fun `confidence is bounded 0 to 1`() {
        val conf = RoleClassifier.computeConfidence(
            0.99, 0.99, 0.5, 0.5, 0.1, 0.1, nEff = 1000,
        )
        assertTrue(conf in 0.0..1.0, "conf=$conf")
    }

    // --- Reference quadrant ---

    @Test
    fun `reference quadrant classification`() {
        assertEquals(
            ReferenceQuadrant.HIGH_ABS_BASE,
            RoleCombinator.classifyReference(0.80, 0.78),
        )
        assertEquals(
            ReferenceQuadrant.LOW_ABS_BASE,
            RoleCombinator.classifyReference(0.70, 0.78),
        )
    }

    // --- ResourceLoader ---

    @Test
    fun `ResourceLoader loads reference_base from classpath`() {
        val loader = ResourceLoader(com.example.lomanalyzer.observability.Logger("test"))
        val ref = loader.loadReferenceBase()
        assertNotNull(ref)
        assertEquals("1.0.0", ref!!.version)
        assertEquals(0.45, ref.gammaUsedInCollection)
        assertEquals(7.8, ref.rawQuantileStatistics.lnF.q50)
        assertEquals(0.78, ref.iBaseThresholds.tauBaseP75)
    }
}
