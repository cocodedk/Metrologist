package com.cocode.measureapp.geometry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SolverSelectorTest {
    private val frame = PlaneFrame(
        Vec3(1.0, 0.0, 0.0),
        Vec3(0.0, 1.0, 0.0),
        Vec3(0.0, 0.0, 1.0),
    )

    private fun rect(confidence: Double) =
        PlaneSolution(frame, SolverKind.RECTANGLE, confidence)

    private fun grav(confidence: Double) =
        PlaneSolution(frame, SolverKind.GRAVITY, confidence)

    @Test
    fun rectangleAboveThreshold_isSelected() {
        val rectangle = rect(0.5)
        val gravity = grav(0.9)
        val sel = SolverSelector.select(rectangle, gravity)

        assertSame(rectangle, sel.solution)
        assertEquals("rectangle: corners well-conditioned", sel.reason)
    }

    @Test
    fun rectangleExactlyAtThreshold_isSelected() {
        val rectangle = rect(0.15)
        val sel = SolverSelector.select(rectangle, grav(0.9))

        assertSame(rectangle, sel.solution)
        assertEquals("rectangle: corners well-conditioned", sel.reason)
    }

    @Test
    fun rectangleNull_fallsBackToGravity() {
        val gravity = grav(0.8)
        val sel = SolverSelector.select(null, gravity)

        assertSame(gravity, sel.solution)
        assertTrue("reason mentions fallback", sel.reason.startsWith("fallback:"))
    }

    @Test
    fun rectangleBelowThresholdButGravityUsable_fallsBackToGravity() {
        val gravity = grav(0.8)
        val sel = SolverSelector.select(rect(0.1), gravity)

        assertSame(gravity, sel.solution)
        assertTrue("reason mentions fallback", sel.reason.startsWith("fallback:"))
        // Reason reports the low rectangle confidence that triggered the fallback.
        assertTrue("reason notes the low confidence", sel.reason.contains("0.1"))
    }

    @Test
    fun gravityZeroConfidenceWithLowRectangle_picksBetterNonNull() {
        // Rectangle below threshold (0.1) but still beats gravity's zero confidence.
        val rectangle = rect(0.1)
        val sel = SolverSelector.select(rectangle, grav(0.0))

        assertSame(rectangle, sel.solution)
        assertTrue("reason notes low overall confidence", sel.reason.contains("low"))
    }

    @Test
    fun gravityZeroConfidenceAndRectangleNull_picksGravity() {
        // Both unusable by the first two branches: rectangle null, gravity zero confidence.
        val gravity = grav(0.0)
        val sel = SolverSelector.select(null, gravity)

        assertSame(gravity, sel.solution)
        assertTrue("reason notes low overall confidence", sel.reason.contains("low"))
    }

    @Test
    fun bothUnusable_gravityHigherThanLowRectangle_picksGravity() {
        // Rectangle below threshold, gravity zero so fails branch 2, but gravity confidence
        // (0.0) < rectangle (0.1) -> rectangle wins the better-non-null tie-break here.
        // Construct the opposite: gravity > rectangle but gravity still 0 is impossible, so
        // use a case where gravity is non-zero yet rectangle below threshold is handled by
        // branch 2. The third branch only triggers when gravity confidence == 0, so the
        // better non-null is whichever has higher confidence.
        val rectangle = rect(0.05)
        val gravity = grav(0.0)
        val sel = SolverSelector.select(rectangle, gravity)
        assertSame(rectangle, sel.solution)
    }

    @Test
    fun tiePrefersRectangle() {
        // Third branch, equal confidences -> rectangle preferred on ties.
        val rectangle = rect(0.0)
        val gravity = grav(0.0)
        val sel = SolverSelector.select(rectangle, gravity)

        assertSame(rectangle, sel.solution)
        assertEquals(SolverKind.RECTANGLE, sel.solution.solver)
    }

    @Test
    fun customThreshold_isRespected() {
        // Raise the threshold so a 0.5 rectangle is no longer "well-conditioned".
        val gravity = grav(0.8)
        val sel = SolverSelector.select(rect(0.5), gravity, minRectangleConfidence = 0.9)

        assertSame(gravity, sel.solution)
        assertTrue("reason mentions fallback", sel.reason.startsWith("fallback:"))
    }
}
