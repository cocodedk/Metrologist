package com.cocode.measureapp.geometry

import com.cocode.measureapp.geometry.eligibility.Assessment
import com.cocode.measureapp.geometry.eligibility.IneligibleReason
import com.cocode.measureapp.geometry.eligibility.PlaneAssumption
import com.cocode.measureapp.geometry.eligibility.SurfaceConsistency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ranking happens only among eligible candidates; rejected planes are never revived. */
class SolverSelectorTest {
    private val frame = PlaneFrame(Vec3(1.0, 0.0, 0.0), Vec3(0.0, 1.0, 0.0), Vec3(0.0, 0.0, 1.0))
    private val m = MeasurementResult(2.0, 1.0, 2.0, 2.2, listOf(90.0, 90.0, 90.0, 90.0))

    private fun eligible(solver: SolverKind, confidence: Double, assumption: PlaneAssumption) = Assessment.Eligible(
        solver, frame, m, ScaleResult(1.0, 0.0), confidence, assumption, SurfaceConsistency.CONSISTENT, true, emptyList(),
    )

    private fun rect(confidence: Double) = eligible(SolverKind.RECTANGLE, confidence, PlaneAssumption.RECTANGLE_TARGET)
    private fun wall(confidence: Double) = eligible(SolverKind.GRAVITY, confidence, PlaneAssumption.WALL_FACES_CAMERA)
    private fun floor(confidence: Double) = eligible(SolverKind.GRAVITY, confidence, PlaneAssumption.FLOOR_NORMAL_FROM_GRAVITY)

    private fun rejected(solver: SolverKind, reason: IneligibleReason) =
        Assessment.Ineligible(solver, reason, MeasurementFailureReason.UNSUPPORTED_GEOMETRY, "$solver: $reason")

    private fun chosen(s: Selection) = (s as? Selection.Chosen ?: throw AssertionError("expected a choice, got $s")).chosen

    @Test fun eligibleRectangleOutranksAssumedWallAzimuthEvenWithLowerConfidence() {
        val r = rect(0.2)
        assertSame(r, chosen(SolverSelector.select(r, wall(0.35))))
    }

    @Test fun resolvedCandidatesRankByConfidence() {
        val f = floor(0.9)
        assertSame(f, chosen(SolverSelector.select(rect(0.5), f)))
        val r = rect(0.9)
        assertSame(r, chosen(SolverSelector.select(r, floor(0.5))))
    }

    @Test fun tiesPreferTheRectangle() {
        val r = rect(0.5)
        assertSame(r, chosen(SolverSelector.select(r, floor(0.5))))
    }

    @Test fun highConfidenceIneligibleRectangleIsNeverRevived() {
        // Old behaviour picked any rectangle scoring >= 0.15 on image angles; now it cannot.
        val g = wall(0.3)
        val sel = SolverSelector.select(rejected(SolverKind.RECTANGLE, IneligibleReason.NOT_ORTHOGONAL), g)
        assertSame(g, chosen(sel))
        assertTrue((sel as Selection.Chosen).reason.contains("NOT_ORTHOGONAL"))
    }

    @Test fun ineligibleGravityLeavesTheEligibleRectangle() {
        val r = rect(0.1)
        assertSame(r, chosen(SolverSelector.select(r, rejected(SolverKind.GRAVITY, IneligibleReason.GRAVITY_UNAVAILABLE))))
    }

    @Test fun bothIneligibleIsAnExplicitNoChoice() {
        val a = rejected(SolverKind.RECTANGLE, IneligibleReason.SURFACE_CONTRADICTION)
        val b = rejected(SolverKind.GRAVITY, IneligibleReason.PROJECTION_UNUSABLE)
        val sel = SolverSelector.select(a, b)
        assertEquals(Selection.NoneEligible(a, b), sel)
    }
}
