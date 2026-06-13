package com.cocode.measureapp.core

import com.cocode.measureapp.geometry.Vec2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CornerOrderingTest {
    // A known axis-aligned rectangle, image coords with y pointing down.
    // TL=(0,0)  TR=(10,0)  BR=(10,4)  BL=(0,4)
    private val tl = Vec2(0.0, 0.0)
    private val tr = Vec2(10.0, 0.0)
    private val br = Vec2(10.0, 4.0)
    private val bl = Vec2(0.0, 4.0)
    private val expected = listOf(tl, tr, br, bl)

    @Test fun alreadyOrderedRectangleStaysOrdered() {
        assertEquals(expected, CornerOrdering.order(listOf(tl, tr, br, bl)))
    }

    @Test fun everyPermutationOfRectangleReturnsSameOrder() {
        val base = listOf(tl, tr, br, bl)
        for (perm in permutations(base)) {
            assertEquals(expected, CornerOrdering.order(perm))
        }
    }

    @Test fun rotatedNonAxisAlignedQuadIsOrdered() {
        // A convex quad rotated so no edge is axis-aligned (y down).
        val a = Vec2(2.0, 0.0)   // smallest x+y -> TL
        val b = Vec2(8.0, 3.0)   // smallest y-x -> TR
        val c = Vec2(6.0, 9.0)   // largest x+y -> BR
        val d = Vec2(0.0, 5.0)   // largest y-x -> BL
        val expectedQuad = listOf(a, b, c, d)
        for (perm in permutations(expectedQuad)) {
            assertEquals(expectedQuad, CornerOrdering.order(perm))
        }
    }

    @Test fun perspectiveTrapezoidIsOrdered() {
        // Far edge narrower than near edge (perspective), y down.
        val pTl = Vec2(3.0, 1.0)
        val pTr = Vec2(7.0, 1.0)
        val pBr = Vec2(9.0, 8.0)
        val pBl = Vec2(1.0, 8.0)
        val exp = listOf(pTl, pTr, pBr, pBl)
        for (perm in permutations(exp)) {
            assertEquals(exp, CornerOrdering.order(perm))
        }
    }

    @Test fun everyFixtureReturnsFourDistinctCorners() {
        val fixtures = listOf(
            listOf(tl, tr, br, bl),
            listOf(Vec2(2.0, 0.0), Vec2(8.0, 3.0), Vec2(6.0, 9.0), Vec2(0.0, 5.0)),
            listOf(Vec2(3.0, 1.0), Vec2(7.0, 1.0), Vec2(9.0, 8.0), Vec2(1.0, 8.0)),
        )
        for (quad in fixtures) {
            val result = CornerOrdering.order(quad)
            assertEquals(4, result.toSet().size)
            assertEquals(quad.toSet(), result.toSet())
        }
    }

    // Finding: diamond-oriented (~45deg) square must NOT collapse two corners.
    // Square rotated 45deg, image coords y-down: top, right, bottom, left.
    @Test fun diamondOrientedQuadReturnsFourDistinctCornersClockwise() {
        val top = Vec2(5.0, 0.0)
        val right = Vec2(10.0, 5.0)
        val bottom = Vec2(5.0, 10.0)
        val left = Vec2(0.0, 5.0)
        val diamond = listOf(top, right, bottom, left)
        // Clockwise (y-down) starting from the topmost vertex.
        val exp = listOf(top, right, bottom, left)
        for (perm in permutations(diamond)) {
            val result = CornerOrdering.order(perm)
            assertEquals(4, result.toSet().size)
            assertEquals(exp, result)
        }
    }

    // Finding: tie on a 45deg edge (two points share x+y) must stay permutation-invariant.
    @Test fun quadWith45DegEdgeIsPermutationInvariant() {
        // (1,3) and (3,1) both have x+y == 4 (an edge along the -45deg diagonal).
        val quad = listOf(Vec2(1.0, 3.0), Vec2(3.0, 1.0), Vec2(8.0, 2.0), Vec2(5.0, 8.0))
        val results = permutations(quad).map { CornerOrdering.order(it) }.toSet()
        assertEquals(1, results.size)
        assertEquals(4, results.first().toSet().size)
    }

    // Finding: strongly sheared but convex quad keeps all four corners distinct.
    @Test fun stronglyShearedConvexQuadKeepsFourDistinctCorners() {
        val quad = listOf(Vec2(0.0, 0.0), Vec2(20.0, 1.0), Vec2(25.0, 5.0), Vec2(2.0, 4.0))
        for (perm in permutations(quad)) {
            val result = CornerOrdering.order(perm)
            assertEquals(4, result.toSet().size)
            assertEquals(quad.toSet(), result.toSet())
        }
    }

    // Finding: genuinely degenerate input (duplicate / collapsed corner) fails loudly.
    @Test fun degenerateQuadWithDuplicatePointThrows() {
        val ex = assertThrows(IllegalArgumentException::class.java) {
            CornerOrdering.order(listOf(Vec2(0.0, 0.0), Vec2(0.0, 0.0), Vec2(10.0, 4.0), Vec2(0.0, 4.0)))
        }
        assertTrue(ex.message!!.contains("distinct"))
    }

    @Test fun sizeNotFourThrows() {
        assertThrows(IllegalArgumentException::class.java) {
            CornerOrdering.order(listOf(tl, tr, br))
        }
        assertThrows(IllegalArgumentException::class.java) {
            CornerOrdering.order(listOf(tl, tr, br, bl, tl))
        }
        assertThrows(IllegalArgumentException::class.java) {
            CornerOrdering.order(emptyList())
        }
    }

    private fun <T> permutations(items: List<T>): List<List<T>> {
        if (items.size <= 1) return listOf(items)
        val result = mutableListOf<List<T>>()
        for (i in items.indices) {
            val rest = items.toMutableList().apply { removeAt(i) }
            for (p in permutations(rest)) result.add(listOf(items[i]) + p)
        }
        return result
    }
}
