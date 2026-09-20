package com.cocode.measureapp.geometry

/** Numeric guards shared across the geometry primitives. */
object Tolerances {
    /** Unit-scale guard for normalization / matrix invertibility. */
    const val NORM_EPS = 1e-12

    /** Image-scale guard for parallel lines / projective degeneracy. */
    const val PROJ_EPS = 1e-9

    /*
     * Marker quadrilateral degeneracy guards. They apply to marks in the canonical
     * image-pixel frame (never display/screen pixels) and are DIMENSIONLESS: each compares a
     * quantity against the quad's own diameter (largest vertex-to-vertex distance), so any
     * uniform scaling — display zoom, pan, or image resolution — yields the same outcome.
     */

    /** Minimum vertex separation as a fraction of the quad diameter (covers every edge). */
    const val MARKER_MIN_SEPARATION_FRACTION = 1e-3

    /** Minimum |sin| of the turn at every vertex; below it three vertices are collinear. */
    const val MARKER_MIN_TURN_SIN = 1e-3

    /** Minimum enclosed area as a fraction of the squared quad diameter. */
    const val MARKER_MIN_AREA_FRACTION = 1e-4
}
