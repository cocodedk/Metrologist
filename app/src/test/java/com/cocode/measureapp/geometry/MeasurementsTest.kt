package com.cocode.measureapp.geometry

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.sqrt

class MeasurementsTest {

    private fun rect3x2() = listOf(
        Vec2(0.0, 0.0), // TL
        Vec2(3.0, 0.0), // TR
        Vec2(3.0, 2.0), // BR
        Vec2(0.0, 2.0), // BL
    )

    @Test fun axisAlignedRectangle() {
        val r = Measurements.compute(rect3x2())
        assertEquals(3.0, r.width, 1e-12)
        assertEquals(2.0, r.height, 1e-12)
        assertEquals(6.0, r.area, 1e-12)
        assertEquals(sqrt(13.0), r.diagonal, 1e-12) // ~3.60555
        assertEquals(4, r.cornerAngles.size)
        r.cornerAngles.forEach { assertEquals(90.0, it, 1e-9) }
    }

    @Test fun unitSquare() {
        val square = listOf(
            Vec2(0.0, 0.0),
            Vec2(1.0, 0.0),
            Vec2(1.0, 1.0),
            Vec2(0.0, 1.0),
        )
        val r = Measurements.compute(square)
        assertEquals(1.0, r.width, 1e-12)
        assertEquals(1.0, r.height, 1e-12)
        assertEquals(1.0, r.area, 1e-12)
        assertEquals(sqrt(2.0), r.diagonal, 1e-12)
        r.cornerAngles.forEach { assertEquals(90.0, it, 1e-9) }
    }

    @Test fun knownParallelogram() {
        // TL=(0,0) TR=(2,0) BR=(3,2) BL=(1,2): a slanted parallelogram.
        val p = listOf(
            Vec2(0.0, 0.0),
            Vec2(2.0, 0.0),
            Vec2(3.0, 2.0),
            Vec2(1.0, 2.0),
        )
        val r = Measurements.compute(p)
        // width = mean(|TR-TL|, |BR-BL|) = mean(2, 2) = 2.
        assertEquals(2.0, r.width, 1e-12)
        // height = mean(|BL-TL|, |BR-TR|) = mean(sqrt(5), sqrt(5)) = sqrt(5).
        assertEquals(sqrt(5.0), r.height, 1e-12)
        // shoelace area = base(2) * height(2) = 4.
        assertEquals(4.0, r.area, 1e-12)
        // diagonal = mean(|BR-TL|, |BL-TR|) = mean(sqrt(13), sqrt(5)).
        assertEquals((sqrt(13.0) + sqrt(5.0)) / 2.0, r.diagonal, 1e-12)
        // hand-computed interior angles (degrees):
        assertEquals(63.43494882292201, r.cornerAngles[0], 1e-9)
        assertEquals(116.56505117707799, r.cornerAngles[1], 1e-9)
        assertEquals(63.43494882292201, r.cornerAngles[2], 1e-9)
        assertEquals(116.56505117707799, r.cornerAngles[3], 1e-9)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsWrongCornerCount() {
        Measurements.compute(listOf(Vec2(0.0, 0.0), Vec2(1.0, 0.0), Vec2(1.0, 1.0)))
    }

    /**
     * Degenerate quad: TR coincides with TL, so the TL->TR edge has zero length.
     * The interior-angle denominator (prev.norm() * next.norm()) would be 0.0,
     * yielding 0.0/0.0 = NaN. The guard must reject this rather than emit NaN angles.
     */
    @Test(expected = IllegalArgumentException::class)
    fun rejectsDegenerateQuadWithDuplicateAdjacentCorner() {
        Measurements.compute(
            listOf(
                Vec2(0.0, 0.0), // TL
                Vec2(0.0, 0.0), // TR == TL  (zero-length edge)
                Vec2(3.0, 2.0), // BR
                Vec2(0.0, 2.0), // BL
            ),
        )
    }

    /**
     * Degenerate quad where the PREVIOUS edge is zero-length at corner 0: BL == TL,
     * so at corner TL (i=0) prev = BL-TL = 0 (pn <= NORM_EPS) and the guard's first
     * operand is already false, short-circuiting before nn is ever tested.
     *
     * This is the only arrangement that exercises the `pn > NORM_EPS` == false branch
     * in isolation. A single other duplicated-corner pair instead trips the SECOND
     * operand (nn <= NORM_EPS) at the earlier index first: e.g. TR == BR throws at
     * corner TR via the next edge before corner BR's prev edge is reached. Putting the
     * duplicate at BL == TL forces corner 0 to fail on its prev edge before any corner
     * with a zero next edge runs.
     */
    @Test(expected = IllegalArgumentException::class)
    fun rejectsDegenerateQuadWithZeroLengthPrevEdge() {
        Measurements.compute(
            listOf(
                Vec2(0.0, 0.0), // TL
                Vec2(3.0, 0.0), // TR
                Vec2(3.0, 2.0), // BR
                Vec2(0.0, 0.0), // BL == TL  (zero-length prev edge at TL, i=0)
            ),
        )
    }

    /**
     * Near-collinear corners: TL, TR, BR almost lie on a straight line, so the raw
     * dot/(norm*norm) ratio at TR rounds just outside [-1, 1]. The coerceIn clamp
     * must execute and keep the angle finite (~180 deg) instead of acos(NaN) = NaN.
     */
    @Test fun clampsNearCollinearCornerToFiniteAngle() {
        // TL, TR, BR are exactly collinear (BR = 2*TR), so at TR the two edges point in
        // opposite directions. The raw dot/(norm*norm) ratio rounds to -1.0000000000000002,
        // i.e. just outside [-1, 1], forcing the coerceIn clamp to fire. Without the clamp
        // acos(-1.0000000000000002) = NaN; with it the angle is a finite 180 deg.
        val collinearAtTr = listOf(
            Vec2(0.0, 0.0),  // TL
            Vec2(3.0, 0.5),  // TR
            Vec2(6.0, 1.0),  // BR (collinear with TL, TR)
            Vec2(0.0, 2.0),  // BL
        )
        val angleAtTr = Measurements.compute(collinearAtTr).cornerAngles[1]
        assertEquals(true, angleAtTr.isFinite())
        assertEquals(180.0, angleAtTr, 1e-3)
    }
}
