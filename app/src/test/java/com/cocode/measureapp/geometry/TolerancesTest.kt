package com.cocode.measureapp.geometry

import org.junit.Assert.assertEquals
import org.junit.Test

class TolerancesTest {
    @Test fun normEpsIsUnitScaleGuard() {
        assertEquals(1e-12, Tolerances.NORM_EPS, 0.0)
    }

    @Test fun projEpsIsImageScaleGuard() {
        assertEquals(1e-9, Tolerances.PROJ_EPS, 0.0)
    }
}
