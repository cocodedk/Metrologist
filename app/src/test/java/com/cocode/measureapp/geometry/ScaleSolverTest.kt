package com.cocode.measureapp.geometry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScaleSolverTest {

    @Test fun twoPointExactScale() {
        // Metric span of 2.0 for a real stick of length 1.0 -> scale 0.5, perfect agreement.
        val metric = listOf(Vec2(0.0, 0.0), Vec2(2.0, 0.0))
        val res = ScaleSolver.solve(metric, StickProfile(totalLength = 1.0))
        assertEquals(0.5, res.scale, 1e-12)
        assertEquals(0.0, res.agreement, 1e-12)
    }

    @Test fun fivePointsConsistentHasZeroAgreement() {
        // 5 evenly spaced points (4 equal segments), each metric segment 0.5 long, real
        // band length = totalLength/bandCount = 2.0/4 = 0.5 -> est_i = 1.0 for all -> scale 1.0.
        val metric = (0..4).map { Vec2(it * 0.5, 0.0) }
        val res = ScaleSolver.solve(metric, StickProfile(totalLength = 2.0, bandCount = 4))
        assertEquals(1.0, res.scale, 1e-12)
        assertEquals(0.0, res.agreement, 1e-12)
    }

    @Test fun fivePointsOneJointPerturbedKeepsMedianRobust() {
        // Same as above but the 3rd joint is shifted: segments become
        // 0.5, 0.3, 0.7, 0.5 (the outlier pair). Real band length 0.5 each.
        // est = 1.0, 1.6667, 0.7143, 1.0 -> sorted [0.7143, 1.0, 1.0, 1.6667];
        // lower-median order statistic sorted[(4-1)/2] = sorted[1] = 1.0 (an observed estimate).
        val metric = listOf(
            Vec2(0.0, 0.0),
            Vec2(0.5, 0.0),
            Vec2(0.8, 0.0), // perturbed joint (was 1.0)
            Vec2(1.5, 0.0),
            Vec2(2.0, 0.0),
        )
        val res = ScaleSolver.solve(metric, StickProfile(totalLength = 2.0, bandCount = 4))
        assertEquals("median scale robust to single outlier", 1.0, res.scale, 1e-12)
        assertTrue("agreement should reflect the outlier", res.agreement > 0.0)
    }

    @Test fun otherSizeTreatedAsSingleSpan() {
        // 3 points -> not bandCount+1, not 2: single span end-to-end of totalLength.
        // End-to-end metric distance = 0.4 + 0.6 = 1.0; but span is computed point[0]..point[last].
        val metric = listOf(Vec2(0.0, 0.0), Vec2(0.4, 0.0), Vec2(1.0, 0.0))
        val res = ScaleSolver.solve(metric, StickProfile(totalLength = 2.0, bandCount = 4))
        // single span: real length 2.0 over metric span 1.0 -> scale 2.0, agreement 0.0.
        assertEquals(2.0, res.scale, 1e-12)
        assertEquals(0.0, res.agreement, 1e-12)
    }

    @Test(expected = IllegalArgumentException::class)
    fun fewerThanTwoPointsThrows() {
        ScaleSolver.solve(listOf(Vec2(0.0, 0.0)), StickProfile(totalLength = 1.0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun zeroLengthSegmentThrows() {
        ScaleSolver.solve(
            listOf(Vec2(1.0, 1.0), Vec2(1.0, 1.0)),
            StickProfile(totalLength = 1.0),
        )
    }

    @Test fun oddSegmentCountSelectsTrueMiddleEstimate() {
        // bandCount = 3 -> 4 points -> 3 segments (odd length estimate list, size > 1).
        // Metric segments 1.25, 1.0, 0.5; real band length = 3.0/3 = 1.0.
        // est = 0.8, 1.0, 2.0 -> sorted [0.8, 1.0, 2.0], median must be the MIDDLE
        // element 1.0 (not 0.8, not 2.0, not a mean). Catches an off-by-one in mid.
        val metric = listOf(
            Vec2(0.0, 0.0),
            Vec2(1.25, 0.0),
            Vec2(2.25, 0.0),
            Vec2(2.75, 0.0),
        )
        val res = ScaleSolver.solve(metric, StickProfile(totalLength = 3.0, bandCount = 3))
        assertEquals("odd-length median selects the central estimate", 1.0, res.scale, 1e-12)
        assertTrue(res.agreement > 0.0)
    }

    @Test fun evenSegmentCountOneDirectionalCorruptionReturnsObservedEstimate() {
        // 5 points -> 4 segments (even). Two segments corrupted in the SAME direction so the
        // two central order statistics differ: metric segments 0.5, 0.5, 0.385, 0.385;
        // real band length = 2.0/4 = 0.5. est = 1.0, 1.0, 1.3, 1.3 -> sorted
        // [1.0, 1.0, 1.3, 1.3]. The averaging "median" would fabricate 1.15 (no segment
        // produced it); the lower-median order statistic returns 1.0, an actual estimate.
        val metric = listOf(
            Vec2(0.0, 0.0),
            Vec2(0.5, 0.0),
            Vec2(1.0, 0.0),
            Vec2(1.385, 0.0),
            Vec2(1.77, 0.0),
        )
        val res = ScaleSolver.solve(metric, StickProfile(totalLength = 2.0, bandCount = 4))
        assertEquals("even-count median is an observed lower-median estimate", 1.0, res.scale, 1e-12)
    }

    @Test fun tinyButPositiveSegmentIsAccepted() {
        // A legitimate nonzero metric distance below the old NORM_EPS (1e-12) must NOT be
        // rejected: the contract requires each distance > 0, not > 1e-12.
        val metric = listOf(Vec2(0.0, 0.0), Vec2(1e-15, 0.0))
        val res = ScaleSolver.solve(metric, StickProfile(totalLength = 1.0))
        assertEquals(1.0 / 1e-15, res.scale, 1.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun bandCountZeroThrows() {
        StickProfile(totalLength = 1.0, bandCount = 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun bandCountNegativeThrows() {
        StickProfile(totalLength = 1.0, bandCount = -1)
    }

    @Test fun stickProfileWidthDefaultsToZero() {
        assertEquals(0.0, StickProfile(totalLength = 1.0).width, 0.0)
    }

    @Test fun stickProfileWidthGetterReturnsConstructedValue() {
        assertEquals(0.04, StickProfile(totalLength = 1.0, width = 0.04).width, 0.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun stickProfileNegativeWidthThrows() {
        StickProfile(totalLength = 1.0, width = -0.01)
    }
}
