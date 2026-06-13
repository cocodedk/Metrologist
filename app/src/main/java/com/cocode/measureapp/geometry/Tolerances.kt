package com.cocode.measureapp.geometry

/** Numeric guards shared across the geometry primitives. */
object Tolerances {
    /** Unit-scale guard for normalization / matrix invertibility. */
    const val NORM_EPS = 1e-12

    /** Image-scale guard for parallel lines / projective degeneracy. */
    const val PROJ_EPS = 1e-9
}
