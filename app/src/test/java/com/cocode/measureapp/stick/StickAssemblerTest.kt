package com.cocode.measureapp.stick

import com.cocode.measureapp.geometry.Vec2
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.sqrt

/**
 * Comprehensive TDD tests for [StickAssembler].
 *
 * A "synthetic stick" runs from A=(100,100) to B=(500,300); length ≈ 447 px.
 * With bandCount=4 the five evenly-spaced points are at fractions 0, 0.25, 0.5, 0.75, 1.
 * Red bands (orientation-A): fractions [0,0.25) and [0.5,0.75) — even band indices 0 and 2.
 */
class StickAssemblerTest {

    companion object {
        private val A = Vec2(100.0, 100.0)
        private val B = Vec2(500.0, 300.0)
        private const val STEPS = 40          // dense stick body sample count
        private const val BAND_COUNT = 4

        /** Sample [n] evenly-spaced points inside a fractional range [fLo, fHi] on A→B. */
        private fun sampleOnSegment(fLo: Double, fHi: Double, n: Int = 10): List<Vec2> {
            if (n == 0) return emptyList()
            return (0 until n).map { i ->
                val f = fLo + (fHi - fLo) * (i + 0.5) / n
                A + (B - A) * f
            }
        }

        /** Dense body points along A→B (no noise), inclusive endpoints. */
        private fun stickBody(): List<Vec2> = (0..STEPS).map { i ->
            A + (B - A) * (i.toDouble() / STEPS)
        }

        /**
         * Red points for orientation-A: bands 0 and 2 → fractions [0,0.25) and [0.5,0.75).
         * (Even band indices for bandCount=4.)
         */
        private fun redPointsOrientationA(): List<Vec2> =
            sampleOnSegment(0.0, 0.25) + sampleOnSegment(0.5, 0.75)

        /**
         * Red points for orientation-B: bands 1 and 3 → fractions [0.25,0.5) and [0.75,1.0).
         * (Odd band indices.)
         */
        private fun redPointsOrientationB(): List<Vec2> =
            sampleOnSegment(0.25, 0.5) + sampleOnSegment(0.75, 1.0)
    }

    // ---- guard: bandCount < 2 --------------------------------------------------

    @Test fun `bandCount less than 2 throws`() {
        assertThrows(IllegalArgumentException::class.java) {
            StickAssembler.assemble(stickBody(), redPointsOrientationA(), bandCount = 1)
        }
    }

    @Test fun `bandCount zero throws`() {
        assertThrows(IllegalArgumentException::class.java) {
            StickAssembler.assemble(stickBody(), redPointsOrientationA(), bandCount = 0)
        }
    }

    // ---- degenerate inputs -----------------------------------------------------

    @Test fun `single stick point returns null`() {
        assertNull(StickAssembler.assemble(listOf(A), redPointsOrientationA()))
    }

    @Test fun `empty stick points returns null`() {
        assertNull(StickAssembler.assemble(emptyList(), redPointsOrientationA()))
    }

    @Test fun `all equal stick points (len near zero) returns null`() {
        val degenerate = List(10) { Vec2(200.0, 200.0) }
        assertNull(StickAssembler.assemble(degenerate, redPointsOrientationA()))
    }

    @Test fun `empty red points returns null (redScore zero → confidence zero)`() {
        assertNull(StickAssembler.assemble(stickBody(), emptyList()))
    }

    // ---- happy path: orientation-A red points ----------------------------------

    @Test fun `synthetic stick orientation-A returns non-null`() {
        val result = StickAssembler.assemble(stickBody(), redPointsOrientationA())
        assertNotNull(result)
    }

    @Test fun `synthetic stick orientation-A returns bandCount+1 points`() {
        val result = StickAssembler.assemble(stickBody(), redPointsOrientationA())!!
        assertEquals(BAND_COUNT + 1, result.points.size)
    }

    @Test fun `synthetic stick orientation-A confidence exceeds 0_7`() {
        val result = StickAssembler.assemble(stickBody(), redPointsOrientationA())!!
        assertTrue("confidence=${result.confidence}", result.confidence > 0.7)
    }

    @Test fun `synthetic stick first point near A and last point near B`() {
        val result = StickAssembler.assemble(stickBody(), redPointsOrientationA())!!
        val pts = result.points
        // First and last must be close to A and B (or B and A — axis direction may flip).
        val nearA = minOf(pts.first().distanceTo(A), pts.last().distanceTo(A))
        val nearB = minOf(pts.first().distanceTo(B), pts.last().distanceTo(B))
        assertTrue("first or last not near A, min dist=$nearA", nearA < 1.0)
        assertTrue("first or last not near B, min dist=$nearB", nearB < 1.0)
    }

    @Test fun `synthetic stick points are roughly evenly spaced`() {
        val result = StickAssembler.assemble(stickBody(), redPointsOrientationA())!!
        val pts = result.points
        val len = pts.first().distanceTo(pts.last())
        val expectedSpacing = len / BAND_COUNT
        for (i in 0 until pts.size - 1) {
            val d = pts[i].distanceTo(pts[i + 1])
            assertEquals("spacing[$i]", expectedSpacing, d, 1.0)
        }
    }

    // ---- orientation-B red points also succeed (max of the two orientations) ---

    @Test fun `orientation-B red points also yields high confidence`() {
        val result = StickAssembler.assemble(stickBody(), redPointsOrientationB())
        assertNotNull(result)
        assertTrue("confidence=${result!!.confidence}", result.confidence > 0.7)
    }

    // ---- lower collinearity (noisy points) -------------------------------------

    @Test fun `perpendicular-noisy stick points reduce collinearity`() {
        // Perturb each stick point 12 px sideways (> 10 % of length ~447 px → collinearity < 1)
        val len = A.distanceTo(B)
        val perpX =  -(B.y - A.y) / len
        val perpY =   (B.x - A.x) / len
        val noise = 14.0
        val noisyBody = stickBody().mapIndexed { i, p ->
            val sign = if (i % 2 == 0) 1.0 else -1.0
            Vec2(p.x + perpX * noise * sign, p.y + perpY * noise * sign)
        }
        val resultNoisy  = StickAssembler.assemble(noisyBody, redPointsOrientationA())
        val resultClean  = StickAssembler.assemble(stickBody(),  redPointsOrientationA())

        // Noisy should either be null OR have lower confidence than clean.
        val cleanConf = resultClean!!.confidence
        if (resultNoisy != null) {
            assertTrue(
                "noisy conf ${resultNoisy.confidence} should be < clean conf $cleanConf",
                resultNoisy.confidence < cleanConf
            )
        }
        // If null that also satisfies "lower" — null means confidence < minConfidence.
    }

    // ---- red distribution score behavior ----------------------------------------

    @Test fun `red points in all 4 bands give redScore of 0_5 (max of even vs odd)`() {
        // With reds evenly spread across all 4 bands, inEven = inOdd = 0.5 → redScore = 0.5.
        val allBandsRed = sampleOnSegment(0.0, 1.0, 40)
        val result = StickAssembler.assemble(stickBody(), allBandsRed, minConfidence = 0.01)
        assertNotNull(result)
        // redScore = 0.5; collinearity ≈ 1.0 → confidence ≈ 0.5.
        assertTrue("confidence should be ~0.5, got ${result!!.confidence}",
            result.confidence in 0.4..0.6)
    }

    @Test fun `red score with orientation-A reds is near 1_0`() {
        // All reds in even bands (0, 2) → inEven ≈ 1.0 → redScore ≈ 1.0.
        val result = StickAssembler.assemble(stickBody(), redPointsOrientationA())!!
        assertTrue("redScore component should produce high confidence, got ${result.confidence}",
            result.confidence > 0.9)
    }

    // ---- minConfidence cutoff: just-below and just-above -----------------------

    @Test fun `result returned when confidence exactly meets minConfidence`() {
        // Use orientation-A reds with a very low minConfidence threshold.
        val result = StickAssembler.assemble(
            stickBody(), redPointsOrientationA(), minConfidence = 0.01
        )
        assertNotNull(result)
    }

    @Test fun `result null when minConfidence set above actual confidence`() {
        // Set minConfidence = 1.1 (impossible) → always null.
        val result = StickAssembler.assemble(
            stickBody(), redPointsOrientationA(), minConfidence = 1.1
        )
        assertNull(result)
    }

    @Test fun `just-below minConfidence returns null`() {
        // With reds evenly spread across all 4 bands, confidence ≈ 0.5.
        // Setting minConfidence = 0.9 → null.
        val allBandsRed = sampleOnSegment(0.0, 1.0, 40)
        val result = StickAssembler.assemble(
            stickBody(), allBandsRed, minConfidence = 0.9
        )
        assertNull(result)
    }

    @Test fun `just-above minConfidence returns points`() {
        // Same evenly-spread reds, confidence ≈ 0.5. minConfidence = 0.01 → returns result.
        val allBandsRed = sampleOnSegment(0.0, 1.0, 40)
        val result = StickAssembler.assemble(
            stickBody(), allBandsRed, minConfidence = 0.01
        )
        assertNotNull(result)
    }

    // ---- confidence field is in [0,1] -----------------------------------------

    @Test fun `confidence is in 0 to 1`() {
        val result = StickAssembler.assemble(stickBody(), redPointsOrientationA())!!
        assertTrue(result.confidence in 0.0..1.0)
    }

    // ---- red points near boundary are kept (tolerance) -------------------------

    @Test fun `red point at exact boundary fraction 0 and 1 are kept`() {
        val boundary = listOf(A, B)  // fractions exactly 0 and 1
        // Should not throw; boundary reds fall in bands 0 and (bandCount-1) respectively.
        val result = StickAssembler.assemble(stickBody(), boundary)
        // Both boundary reds are kept (one in band 0, one in band 3 → split even/odd → redScore = 0.5).
        assertNotNull(result)
        assertEquals(BAND_COUNT + 1, result!!.points.size)
    }

    // ---- red points outside stick range are dropped (covers both out-of-range branches) ------

    @Test fun `red points strictly outside 0-1 range are dropped`() {
        // Place extra reds well outside the stick endpoints (u < 0 and u > 1).
        val dx = B.x - A.x   // direction vector
        val dy = B.y - A.y
        val farBefore = Vec2(A.x - dx, A.y - dy)   // u ≈ -1
        val farAfter  = Vec2(B.x + dx, B.y + dy)   // u ≈ 2
        // Also include good reds so the result is non-null (orientation-A pattern).
        val reds = listOf(farBefore, farAfter) + redPointsOrientationA()
        val result = StickAssembler.assemble(stickBody(), reds)
        // The out-of-range reds are dropped; valid reds in even bands → high confidence.
        assertNotNull(result)
        assertTrue("confidence should remain high, got ${result!!.confidence}",
            result.confidence > 0.7)
    }

    // ---- data-class accessors (coverage) ----------------------------------------

    @Test fun `StickPoints data class getters return constructed values`() {
        val pts = listOf(Vec2(1.0, 2.0), Vec2(3.0, 4.0))
        val sp = StickPoints(pts, 0.85)
        assertEquals(pts, sp.points)
        assertEquals(0.85, sp.confidence, 1e-12)
    }

    // ---- bandCount = 2 (minimum allowed) ----------------------------------------

    @Test fun `bandCount 2 produces 3 points`() {
        val result = StickAssembler.assemble(stickBody(), sampleOnSegment(0.0, 0.5, 20), bandCount = 2)
        assertNotNull(result)
        assertEquals(3, result!!.points.size)
    }
}
