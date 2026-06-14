package com.cocode.measureapp.stick

import com.cocode.measureapp.geometry.StickProfile
import com.cocode.measureapp.geometry.Vec2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TDD spec for [StickScale.solve] — recovering the metric-to-real scale from the stick's 4
 * box corners already projected onto the metric plane, using BOTH the known length (long
 * edges) and, when provided, the known width (short edges).
 *
 * Boxes are given as 4 corners ordered around the quad `[c0, c1, c2, c3]`. The long edge pair
 * are the two longer opposite edges, the short edge pair the two shorter; [StickScale]
 * averages each pair before forming the per-dimension scale estimate.
 */
class StickScaleTest {

    /** Axis-aligned box `length` x `width` (long along x), corners clockwise from origin. */
    private fun box(length: Double, width: Double): List<Vec2> = listOf(
        Vec2(0.0, 0.0), Vec2(length, 0.0), Vec2(length, width), Vec2(0.0, width),
    )

    @Test fun lengthOnlyProfileUsesLongEdgesScaleLenOnly() {
        // Metric long edges = 2.0 for a real length of 1.0 -> scale 0.5. width = 0 -> ignored.
        val metric = box(length = 2.0, width = 0.5)
        val res = StickScale.solve(metric, StickProfile(totalLength = 1.0))
        assertEquals(0.5, res.scale, 1e-12)
        assertEquals("width unknown -> no disagreement", 0.0, res.agreement, 1e-12)
    }

    @Test fun widthKnownAndConsistentGivesSameScaleZeroAgreement() {
        // Real 1.0 x 0.25; metric box 2.0 x 0.5 -> scaleLen = 0.5, scaleWid = 0.25/0.5 = 0.5.
        // Both agree -> combined scale 0.5, agreement 0.
        val metric = box(length = 2.0, width = 0.5)
        val res = StickScale.solve(metric, StickProfile(totalLength = 1.0, width = 0.25))
        assertEquals(0.5, res.scale, 1e-12)
        assertEquals(0.0, res.agreement, 1e-12)
    }

    @Test fun widthKnownButDisagreeingProducesNonZeroAgreement() {
        // Real 1.0 x 0.5; metric box 2.0 x 0.5 -> scaleLen = 0.5, scaleWid = 0.5/0.5 = 1.0.
        // Length-weighted combine: (1.0*0.5 + 0.5*1.0)/(1.0+0.5) = 1.0/1.5 = 0.6667.
        val metric = box(length = 2.0, width = 0.5)
        val res = StickScale.solve(metric, StickProfile(totalLength = 1.0, width = 0.5))
        assertEquals(1.0 / 1.5, res.scale, 1e-9)
        // agreement = |scaleLen - scaleWid| / scale = 0.5 / 0.6667 = 0.75
        assertEquals(0.5 / (1.0 / 1.5), res.agreement, 1e-9)
        assertTrue(res.agreement > 0.0)
    }

    @Test fun lengthWeightedCombineFavorsLengthForLongThinStick() {
        // A long thin stick weights the (more reliable) length estimate more heavily than width.
        // Real 4.0 x 0.1; metric 8.0 x 0.1 -> scaleLen = 0.5, scaleWid = 0.1/0.1 = 1.0.
        // combine = (4.0*0.5 + 0.1*1.0)/(4.0+0.1) = 2.1/4.1 ~ 0.5122 (close to scaleLen).
        val metric = box(length = 8.0, width = 0.1)
        val res = StickScale.solve(metric, StickProfile(totalLength = 4.0, width = 0.1))
        assertEquals(2.1 / 4.1, res.scale, 1e-9)
        assertTrue("combined scale is pulled toward scaleLen", res.scale < 0.55)
    }

    @Test fun cornerOrderDoesNotMatterTallBox() {
        // Same box rotated so the long edge is the 1-2 / 3-0 pair (tall orientation in input).
        val metric = listOf(
            Vec2(0.0, 0.0), Vec2(0.5, 0.0), Vec2(0.5, 2.0), Vec2(0.0, 2.0),
        )
        val res = StickScale.solve(metric, StickProfile(totalLength = 1.0, width = 0.25))
        assertEquals(0.5, res.scale, 1e-12)
        assertEquals(0.0, res.agreement, 1e-12)
    }

    @Test(expected = IllegalArgumentException::class)
    fun wrongCornerCountThrows() {
        StickScale.solve(listOf(Vec2(0.0, 0.0), Vec2(1.0, 0.0)), StickProfile(totalLength = 1.0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun degenerateZeroLongEdgeThrows() {
        // All four corners coincide -> zero long edge -> cannot recover scale.
        val metric = listOf(Vec2(0.0, 0.0), Vec2(0.0, 0.0), Vec2(0.0, 0.0), Vec2(0.0, 0.0))
        StickScale.solve(metric, StickProfile(totalLength = 1.0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun degenerateZeroShortEdgeWithKnownWidthThrows() {
        // Collapsed short edges (a line) but width is known -> width scale undefined -> reject.
        val metric = listOf(
            Vec2(0.0, 0.0), Vec2(2.0, 0.0), Vec2(2.0, 0.0), Vec2(0.0, 0.0),
        )
        StickScale.solve(metric, StickProfile(totalLength = 1.0, width = 0.25))
    }

    @Test fun degenerateZeroShortEdgeWithUnknownWidthIsAccepted() {
        // Collapsed short edges but width unknown (0.0): only the long edge is used -> fine.
        val metric = listOf(
            Vec2(0.0, 0.0), Vec2(2.0, 0.0), Vec2(2.0, 0.0), Vec2(0.0, 0.0),
        )
        val res = StickScale.solve(metric, StickProfile(totalLength = 1.0))
        assertEquals(0.5, res.scale, 1e-12)
        assertEquals(0.0, res.agreement, 1e-12)
    }
}
