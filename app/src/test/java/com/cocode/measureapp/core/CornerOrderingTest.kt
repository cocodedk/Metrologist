package com.cocode.measureapp.core

import com.cocode.measureapp.geometry.Vec2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
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
