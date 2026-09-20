package com.cocode.measureapp.geometry.eligibility

import com.cocode.measureapp.core.DiagnosticsText
import com.cocode.measureapp.geometry.CameraIntrinsics
import com.cocode.measureapp.geometry.ContractScenes
import com.cocode.measureapp.geometry.MeasurementOutcome
import com.cocode.measureapp.geometry.RectangleSolver
import com.cocode.measureapp.geometry.SceneRotations
import com.cocode.measureapp.geometry.SolverKind
import com.cocode.measureapp.geometry.StickProfile
import com.cocode.measureapp.geometry.SurfaceOrientation
import com.cocode.measureapp.geometry.Vec3
import com.cocode.measureapp.geometry.eligibility.EligibilityFixtures as F
import com.cocode.measureapp.geometry.frames.CalibrationProvenance
import com.cocode.measureapp.geometry.frames.CalibrationStatus
import com.cocode.measureapp.geometry.rectangle.RectangleCandidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue

/** Valid rectangles, thresholds on both sides, marking noise and focal error. */
object ToleranceCases {
    private val oblique = ContractScenes.wall(SceneRotations.yawPitch(30.0, 20.0))
    private val obliqueNormal: Vec3 = oblique.r * Vec3(0.0, 0.0, 1.0)

    /** Node 01's poses, including vanishing points at infinity, stay eligible and exact. */
    fun validRectanglesStayEligible() {
        val poses = listOf(0.0 to 0.0, 30.0 to 0.0, 45.0 to 0.0, 60.0 to 0.0, 0.0 to 30.0, 25.0 to 20.0)
        for ((yaw, pitch) in poses) {
            val label = "yaw $yaw pitch $pitch"
            val s = ContractScenes.wall(SceneRotations.yawPitch(yaw, pitch))
            val i = F.scene(s, F.available(s.gravityCam))
            assertEquals(label, SurfaceConsistency.CONSISTENT, F.eligible(F.rectangle(i), label).surface)
            val ok = F.success(F.evaluate(i), label)
            assertEquals(label, SolverKind.RECTANGLE, ok.solver)
            assertEquals(2.0, ok.measurement.width, 2e-6)
            assertEquals(1.0, ok.measurement.height, 1e-6)
        }
    }

    /** Orthogonality and stick-aspect limits, each exercised just inside and just outside. */
    fun thresholdsHoldOnBothSides() {
        val i = F.scene(oblique, F.available(oblique.gravityCam))
        val c = RectangleSolver.candidate(i.corners, i.intrinsics, i.calibration) as RectangleCandidate.Accepted
        val limit = EligibilityTolerances.MAX_ORTHOGONALITY_RESIDUAL
        fun withResidual(r: Double) = c.copy(evidence = c.evidence.copy(orthogonalityResidual = r))
        F.eligible(RectangleEligibility.assess(withResidual(limit * 0.99), i), "residual below")
        val over = F.ineligible(RectangleEligibility.assess(withResidual(limit * 1.01), i), "residual above")
        assertEquals(IneligibleReason.NOT_ORTHOGONAL, over.reason)
        // Declared stick width off by eps gives agreement ~ eps: 9.5% passes, 10.5% fails the 10% limit.
        fun profile(eps: Double) = StickProfile(1.0, 4, 0.04 * (1.0 + eps))
        val inside = F.eligible(F.rectangle(i.copy(profile = profile(0.0955))), "stick inside")
        assertTrue("agreement ${inside.scale.agreement}", inside.scale.agreement in 0.09..0.10)
        val outside = F.ineligible(F.rectangle(i.copy(profile = profile(0.106))), "stick outside")
        assertEquals(IneligibleReason.REFERENCE_INCONSISTENT, outside.reason)
    }

    /** Rectangle normals 4.9 / 5.1 degrees from the selected wall or floor constraint. */
    fun surfaceToleranceBothSides() {
        val g = oblique.gravityCam
        for ((deg, accept) in listOf(4.9 to true, 5.1 to false)) {
            val wall = F.scene(oblique, F.available(F.turn(g, obliqueNormal, deg)), SurfaceOrientation.VERTICAL)
            val floor = F.scene(oblique, F.available(F.turn(obliqueNormal, g, deg)), SurfaceOrientation.HORIZONTAL)
            for ((label, i) in listOf("wall $deg" to wall, "floor $deg" to floor)) {
                if (accept) {
                    assertEquals(label, SurfaceConsistency.CONSISTENT, F.eligible(F.rectangle(i), label).surface)
                    F.success(F.evaluate(i), label)
                } else {
                    assertEquals(label, IneligibleReason.SURFACE_CONTRADICTION, F.ineligible(F.rectangle(i), label).reason)
                    (F.evaluate(i) as? MeasurementOutcome.Success)?.let { assertNotEquals(label, SolverKind.RECTANGLE, it.solver) }
                }
            }
        }
    }

    /** A true rectangle 45 degrees from both constraints: rejected with gravity, unverified without. */
    fun planeFarFromBothConstraints() {
        val tilted = F.available(F.turn(oblique.gravityCam, obliqueNormal, 45.0))
        for (o in SurfaceOrientation.values()) {
            val i = F.scene(oblique, tilted, o)
            assertEquals("$o", IneligibleReason.SURFACE_CONTRADICTION, F.ineligible(F.rectangle(i), "$o").reason)
            (F.evaluate(i) as? MeasurementOutcome.Success)?.let { assertNotEquals("$o", SolverKind.RECTANGLE, it.solver) }
        }
        val ok = F.success(F.evaluate(F.scene(oblique, F.MISSING)), "no gravity")
        assertEquals(SolverKind.RECTANGLE, ok.solver)
        val d = ok.diagnostics!!
        assertEquals(SurfaceConsistency.UNVERIFIED_NO_GRAVITY, d.surfaceConsistency)
        assertTrue(ok.confidence <= EligibilityTolerances.UNVERIFIED_SURFACE_CAP)
        assertTrue(DiagnosticsText.caveats(d).any { it.contains("STALE_SAMPLE") && it.contains("not be verified") })
    }

    /** Half-pixel marking noise at yaw 30 / pitch 20 in both signs: eligible, within 2%. */
    fun halfPixelNoiseStaysEligible() {
        for (sign in listOf(1.0, -1.0)) {
            val marks = ContractScenes.offset(oblique.cornerPixels, ContractScenes.halfPixelOffsets(sign))
            val i = F.scene(oblique, F.available(oblique.gravityCam), corners = marks)
            F.eligible(F.rectangle(i), "noise $sign")
            val ok = F.success(F.evaluate(i), "noise $sign")
            assertEquals(SolverKind.RECTANGLE, ok.solver)
            assertEquals(2.0, ok.measurement.width, 2.0 * 0.02)
            assertEquals(1.0, ok.measurement.height, 1.0 * 0.02)
        }
    }

    /** +-5% focal error labelled approximate: a qualified success or a structured rejection. */
    fun focalErrorIsQualified() {
        for (factor in listOf(1.05, 0.95)) {
            val k = CameraIntrinsics(oblique.k.fx * factor, oblique.k.fy * factor, oblique.k.cx, oblique.k.cy)
            val i = F.scene(oblique, F.available(oblique.gravityCam), k = k, calibration = CalibrationProvenance.FOCAL_AND_SENSOR)
            when (val o = F.evaluate(i)) {
                is MeasurementOutcome.Success -> {
                    F.success(o, "f x $factor")
                    val d = o.diagnostics!!
                    assertEquals(CalibrationStatus.APPROXIMATE, d.calibration!!.status)
                    assertTrue(o.confidence <= EligibilityTolerances.APPROXIMATE_CALIBRATION_CAP)
                    assertTrue(DiagnosticsText.caveats(d).any { it.contains("approximate") })
                }
                is MeasurementOutcome.Failure -> assertTrue(F.failure(o, "f x $factor").detail!!.contains("approximate"))
            }
        }
    }
}
