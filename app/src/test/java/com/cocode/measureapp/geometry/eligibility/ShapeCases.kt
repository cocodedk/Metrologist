package com.cocode.measureapp.geometry.eligibility

import com.cocode.measureapp.core.DiagnosticsText
import com.cocode.measureapp.geometry.ContractScenes
import com.cocode.measureapp.geometry.MeasurementFailureReason
import com.cocode.measureapp.geometry.SceneRotations
import com.cocode.measureapp.geometry.SolverKind
import com.cocode.measureapp.geometry.SurfaceOrientation
import com.cocode.measureapp.geometry.eligibility.EligibilityFixtures as F
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

/** Nonrectangular targets and the unresolved wall azimuth. */
object ShapeCases {
    /** Its rectangle candidate contradicts orthogonality; a candidate rejected by node 01 also counts. */
    private fun assertRectangleRejected(i: EligibilityInput, label: String) {
        val r = F.ineligible(F.rectangle(i), label)
        val expected = setOf(IneligibleReason.NOT_ORTHOGONAL, IneligibleReason.RECTANGLE_REJECTED)
        assertTrue("$label reason ${r.reason}", r.reason in expected)
        assertTrue("$label detail names the method", r.detail.startsWith("Rectangle method"))
    }

    /** Frontal 111.8-degree quad on a wall 4 m away, level phone: truth recovered, assumption explicit. */
    fun nonRectangleWallWithLevelGravity() {
        val i = F.input(F.nonRectCorners, F.nonRectStick, F.available(F.LEVEL), SurfaceOrientation.VERTICAL)
        assertRectangleRejected(i, "nonrectangle wall")
        val truth = F.truth(F.nonRectWorld)
        assertEquals(2.32, truth.area, 1e-12)
        assertEquals(111.8014, truth.cornerAngles[1], 1e-4)
        val s = F.success(F.evaluate(i), "nonrectangle wall")
        assertEquals(SolverKind.GRAVITY, s.solver)
        F.assertMatches("nonrectangle wall", truth, s.measurement, rel = 1e-6, deg = 1e-6)
        // Gravity cannot resolve a wall's azimuth: a level phone must not buy high confidence.
        val d = s.diagnostics!!
        assertEquals(PlaneAssumption.WALL_FACES_CAMERA, d.assumption)
        assertTrue("confidence ${s.confidence} must stay below Medium", s.confidence < 0.4)
        assertTrue(DiagnosticsText.caveats(d).any { it.contains("faces the camera") })
    }

    /** The same quad without usable gravity: an explicit failure naming the missing reading. */
    fun nonRectangleWithoutGravity() {
        val i = F.input(F.nonRectCorners, F.nonRectStick, F.MISSING, SurfaceOrientation.VERTICAL)
        assertRectangleRejected(i, "no gravity")
        val f = F.failure(F.evaluate(i), "no gravity")
        assertEquals(MeasurementFailureReason.METADATA_UNAVAILABLE, f.reason)
        assertTrue(f.detail!!, f.detail!!.contains("STALE_SAMPLE") && f.detail!!.contains("Rectangle method"))
    }

    /** The quad on a table seen straight down: the floor normal from gravity keeps its true shape. */
    fun nonRectangleOnHorizontalPlane() {
        val i = F.input(F.nonRectCorners, F.nonRectStick, F.available(F.STRAIGHT_DOWN), SurfaceOrientation.HORIZONTAL)
        assertRectangleRejected(i, "nonrectangle floor")
        val s = F.success(F.evaluate(i), "nonrectangle floor")
        assertEquals(SolverKind.GRAVITY, s.solver)
        assertEquals(PlaneAssumption.FLOOR_NORMAL_FROM_GRAVITY, s.diagnostics!!.assumption)
        assertEquals(SurfaceConsistency.CONSISTENT, s.diagnostics!!.surfaceConsistency)
        F.assertMatches("nonrectangle floor", F.truth(F.nonRectWorld), s.measurement, rel = 1e-6, deg = 1e-6)
    }

    /**
     * A wall yawed 30 degrees under a level phone: the gravity plane assumes it faces the camera.
     * The stick cross-check may reject that plane; if not, it must stay low and say so.
     */
    fun wallWithUnknownAzimuth() {
        val s = ContractScenes.wall(SceneRotations.yawPitch(30.0, 0.0))
        val i = F.scene(s, F.available(s.gravityCam))
        when (val g = GravityEligibility.assess(i)) {
            is Assessment.Ineligible -> assertEquals(IneligibleReason.REFERENCE_INCONSISTENT, g.reason)
            is Assessment.Eligible -> {
                assertEquals(PlaneAssumption.WALL_FACES_CAMERA, g.assumption)
                assertTrue("assumed azimuth confidence ${g.confidence}", g.confidence <= EligibilityTolerances.ASSUMED_AZIMUTH_CAP)
            }
        }
        // The eligible rectangle resolves the azimuth and outranks the assumption.
        assertEquals(SolverKind.RECTANGLE, F.success(F.evaluate(i), "yaw 30 wall").solver)
    }
}
