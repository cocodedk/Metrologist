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
        // est = 1.0, 1.6667, 0.7143, 1.0 -> median of 4 = mean of two middle = (1.0+1.0)/2 = 1.0.
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
}
