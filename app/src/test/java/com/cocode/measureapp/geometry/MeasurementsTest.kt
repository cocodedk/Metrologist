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
}
