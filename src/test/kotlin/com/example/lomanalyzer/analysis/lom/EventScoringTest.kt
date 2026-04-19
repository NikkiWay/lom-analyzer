package com.example.lomanalyzer.analysis.lom

import com.example.lomanalyzer.observability.Logger
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.math.ln

class EventScoringTest {

    // --- TopicFocusComponent: leave-one-out prior ---

    @Test
    fun `leave-one-out prior gives different values for different authors`() {
        // Author A: 10 topic / 20 all
        // Author B: 2 topic / 50 all
        // Total: 12 topic / 70 all
        val priorA = TopicFocusComponent.computeLeaveOneOutPrior(12, 70, 10, 20)
        val priorB = TopicFocusComponent.computeLeaveOneOutPrior(12, 70, 2, 50)

        // For A: other = (12-10)/(70-20) = 2/50 = 0.04
        assertEquals(0.04, priorA, 0.001)
        // For B: other = (12-2)/(70-50) = 10/20 = 0.5
        assertEquals(0.5, priorB, 0.001)

        // Different priors → different T_raw
        val tRawA = TopicFocusComponent.computeRaw(10, 20, priorA)
        val tRawB = TopicFocusComponent.computeRaw(2, 50, priorB)
        assertNotEquals(tRawA, tRawB, 0.001)
    }

    @Test
    fun `T_raw is capped at 1_0`() {
        // All posts are topical → prior ~ 1.0 → T_raw should be ~1.0
        val prior = TopicFocusComponent.computeLeaveOneOutPrior(100, 100, 10, 10)
        val tRaw = TopicFocusComponent.computeRaw(10, 10, prior)
        assertTrue(tRaw <= 1.0, "tRaw=$tRaw should be <= 1.0")
    }

    @Test
    fun `T_raw smoothing prevents zero for author with no topical posts`() {
        val prior = TopicFocusComponent.computeLeaveOneOutPrior(50, 100, 0, 20)
        val tRaw = TopicFocusComponent.computeRaw(0, 20, prior)
        assertTrue(tRaw > 0, "Should be smoothed above 0, got $tRaw")
    }

    // --- TopicalVolumeComponent: k_window normalization ---

    @Test
    fun `discovery author with half baseline gets higher V_raw`() {
        val topicCount = 10
        // Seed: 60-day baseline → k=1.0
        val kSeed = TopicalVolumeComponent.computeKWindow(60)
        assertEquals(1.0, kSeed, 0.01)
        val vSeed = TopicalVolumeComponent.computeRaw(topicCount, kSeed)

        // Discovery: 30-day baseline → k=0.5
        val kDiscovery = TopicalVolumeComponent.computeKWindow(30)
        assertEquals(0.5, kDiscovery, 0.01)
        val vDiscovery = TopicalVolumeComponent.computeRaw(topicCount, kDiscovery)

        // Same count but shorter baseline → higher normalized volume
        assertTrue(
            vDiscovery > vSeed,
            "Discovery V_raw=$vDiscovery should be > Seed V_raw=$vSeed"
        )
    }

    @Test
    fun `V_raw formula matches spec`() {
        // V_raw = ln(1 + N/k)
        val v = TopicalVolumeComponent.computeRaw(10, 1.0)
        assertEquals(ln(11.0), v, 0.001)
    }

    // --- DisseminationReachComponent ---

    @Test
    fun `M_reach uses collected followers when available`() {
        val reach = DisseminationReachComponent()
        val post = PostReachData(
            postId = 1,
            reposts = 5,
            collectedReposterFollowers = listOf(1000, 2000, 3000, 500, 1500),
            approximated = false,
        )
        val mReach = reach.computeMReach(post, fTilde = 999)
        // Should use actual followers, not fTilde
        assertEquals(8000L, mReach)
    }

    @Test
    fun `M_reach approximates for posts with reposts over 200`() {
        val reach = DisseminationReachComponent()
        val post = PostReachData(
            postId = 1,
            reposts = 300,
            collectedReposterFollowers = emptyList(),
            approximated = true,
        )
        val fTilde = 500
        val mReach = reach.computeMReach(post, fTilde)
        assertEquals(150_000L, mReach)
    }

    @Test
    fun `recompute approximated updates with new F_tilde`() {
        val reach = DisseminationReachComponent()
        val posts = listOf(
            PostReachData(1, 300, emptyList(), approximated = true),
            PostReachData(2, 100, listOf(500, 600), approximated = false),
            PostReachData(3, 250, emptyList(), approximated = true),
        )

        val oldFTilde = 500
        val newFTilde = 800

        val oldReach1 = reach.computeMReach(posts[0], oldFTilde)
        assertEquals(150_000L, oldReach1)

        val recomputed = reach.recomputeApproximated(posts, newFTilde)
        assertEquals(2, recomputed.size, "Only approximated posts")
        assertEquals(1 to 240_000L, recomputed[0])
        assertEquals(3 to 200_000L, recomputed[1])
    }

    @Test
    fun `F_tilde uses reference when fewer than 30 collected`() {
        val reach = DisseminationReachComponent(fTildeReference = 700)
        val fTilde = reach.computeFTilde((1..20).toList())
        assertEquals(700, fTilde, "Should use reference with < 30 collected")
    }

    @Test
    fun `F_tilde uses median when 30+ collected`() {
        val reach = DisseminationReachComponent(fTildeReference = 700)
        val followers = (1..50).map { it * 100 }
        val fTilde = reach.computeFTilde(followers)
        assertEquals(2600, fTilde)
    }

    // --- ContentOriginalityComponent ---

    @Test
    fun `originality raw formula matches spec`() {
        // O_raw = (sum_w + 0.5) / (N + 1)
        val weights = listOf(1.0f, 1.0f, 0.5f, 0.0f, 0.25f) // sum = 2.75
        val oRaw = ContentOriginalityComponent.computeRaw(weights, 5)
        // (2.75 + 0.5) / (5 + 1) = 3.25 / 6 = 0.5417
        assertEquals(0.5417, oRaw, 0.001)
    }

    @Test
    fun `all original posts give high O_raw`() {
        val weights = List(10) { 1.0f }
        val oRaw = ContentOriginalityComponent.computeRaw(weights, 10)
        // (10 + 0.5) / 11 = 0.9545
        assertTrue(oRaw > 0.9, "oRaw=$oRaw")
    }

    // --- EventActivityScorer ---

    @Test
    fun `event scorer produces valid normalized scores`() {
        val scorer = EventActivityScorer(
            normalizer = RobustNormalizer(),
            logger = Logger("test"),
        )

        val authors = (1..20).map { i ->
            AuthorEventData(
                authorId = i,
                topicCount = 5 + i,
                allCount = 20 + i,
                topicEffCount = 5 + i,
                baselineDays = 60,
                discoverySource = "SEED",
                postReaches = (1..5).map { (it * 1000).toLong() },
                originalityWeights = listOf(1.0f, 0.5f, 1.0f),
            )
        }

        val results = scorer.score(authors, totalTopicAll = 250, totalPostsAll = 600)
        assertEquals(20, results.size)

        for (r in results) {
            assertTrue(r.iEvent in 0.0..1.0, "iEvent=${r.iEvent}")
            assertTrue(r.tNorm in 0.0..1.0, "tNorm=${r.tNorm}")
            assertTrue(r.vNorm in 0.0..1.0, "vNorm=${r.vNorm}")
            assertTrue(r.sNorm in 0.0..1.0, "sNorm=${r.sNorm}")
            assertTrue(r.oNorm in 0.0..1.0, "oNorm=${r.oNorm}")
        }
    }

    @Test
    fun `Set B weights sum to 1`() {
        val w = EventWeights.SET_B
        assertEquals(1.0, w.wT + w.wV + w.wS + w.wO, 0.001)
    }

    @Test
    fun `Set A weights sum to 1`() {
        val w = EventWeights.SET_A
        assertEquals(1.0, w.wT + w.wV + w.wS + w.wO, 0.001)
    }
}
