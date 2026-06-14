package com.cocode.measureapp.stick

import com.cocode.measureapp.geometry.Vec2
import org.junit.Assert.assertEquals
import org.junit.Test

class StickBoxTest {
    @Test fun horizontalBoxEndsAreLeftAndRightMidpoints() {
        // wide-short box [TL, TR, BR, BL]; long edge is 0-1, so the short ends are the sides.
        val box = listOf(Vec2(0.0, 0.0), Vec2(10.0, 0.0), Vec2(10.0, 2.0), Vec2(0.0, 2.0))
        val ends = StickBox.ends(box)
        assertEquals(10.0, ends[0].x, 1e-9); assertEquals(1.0, ends[0].y, 1e-9) // right end (10,1)
        assertEquals(0.0, ends[1].x, 1e-9); assertEquals(1.0, ends[1].y, 1e-9)  // left end (0,1)
    }

    @Test fun verticalBoxEndsAreTopAndBottomMidpoints() {
        // tall-narrow box; short edge is 0-1, so the if-branch is taken.
        val box = listOf(Vec2(0.0, 0.0), Vec2(2.0, 0.0), Vec2(2.0, 10.0), Vec2(0.0, 10.0))
        val ends = StickBox.ends(box)
        assertEquals(1.0, ends[0].x, 1e-9); assertEquals(0.0, ends[0].y, 1e-9)  // top end (1,0)
        assertEquals(1.0, ends[1].x, 1e-9); assertEquals(10.0, ends[1].y, 1e-9) // bottom end (1,10)
    }

    @Test(expected = IllegalArgumentException::class)
    fun wrongCornerCountThrows() {
        StickBox.ends(listOf(Vec2(0.0, 0.0), Vec2(1.0, 1.0)))
    }

    @Test fun longShortMeanEdgesWideBox() {
        // wide-short box: long pair (0-1, 2-3) = 10, short pair (1-2, 3-0) = 2.
        val box = listOf(Vec2(0.0, 0.0), Vec2(10.0, 0.0), Vec2(10.0, 2.0), Vec2(0.0, 2.0))
        val (long, short) = StickBox.longShortMeanEdges(box)
        assertEquals(10.0, long, 1e-9)
        assertEquals(2.0, short, 1e-9)
    }

    @Test fun longShortMeanEdgesTallBox() {
        // tall-narrow box: long pair is (1-2, 3-0) = 10, short pair (0-1, 2-3) = 2.
        val box = listOf(Vec2(0.0, 0.0), Vec2(2.0, 0.0), Vec2(2.0, 10.0), Vec2(0.0, 10.0))
        val (long, short) = StickBox.longShortMeanEdges(box)
        assertEquals(10.0, long, 1e-9)
        assertEquals(2.0, short, 1e-9)
    }

    @Test fun longShortMeanEdgesAveragesUnequalOppositeEdges() {
        // Trapezoid: top edge (0-1)=8, bottom edge (2-3)=10 -> long mean 9; the two slanted
        // sides (1-2, 3-0) are each sqrt(1^2 + 3^2) = sqrt(10) -> short mean sqrt(10).
        val box = listOf(Vec2(0.0, 0.0), Vec2(8.0, 0.0), Vec2(9.0, 3.0), Vec2(-1.0, 3.0))
        val (long, short) = StickBox.longShortMeanEdges(box)
        assertEquals(9.0, long, 1e-9)
        assertEquals(kotlin.math.sqrt(10.0), short, 1e-9)
    }

    @Test(expected = IllegalArgumentException::class)
    fun longShortMeanEdgesWrongCornerCountThrows() {
        StickBox.longShortMeanEdges(listOf(Vec2(0.0, 0.0), Vec2(1.0, 1.0)))
    }
}
