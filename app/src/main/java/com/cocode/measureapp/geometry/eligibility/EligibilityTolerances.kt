package com.cocode.measureapp.geometry.eligibility

import com.cocode.measureapp.geometry.rectangle.RectangleTolerances

/**
 * Eligibility thresholds of solver selection (node 03). Each one rejects a candidate that
 * contradicts its own geometric assumption; none of them proves the target is a rectangle.
 * They are consistency tolerances, not claims about camera accuracy.
 */
object EligibilityTolerances {
    /**
     * Maximum `|d1 · d2|` of the unit back-projected rectangle edge directions (cosine of the
     * 3D edge angle, so scale-independent; 0.01 is about 0.57 degrees from square). Exact
     * rectangles give ~1e-12 and the node 03 half-pixel fixture (yaw 30 / pitch 20, 2 x 1 m
     * at 4 m, f = 1000 px) stays near 1e-3, while the frontal 111.8-degree quadrilateral of
     * the acceptance fixture gives ~0.038. A +-5% focal error lands near 0.016, so such scenes
     * may be rejected; that is allowed for approximate calibration.
     */
    const val MAX_ORTHOGONALITY_RESIDUAL = 0.01

    /** Wall: `|n · g| <= sin(5 deg)` for unit plane normal `n` and unit down `g`. */
    const val WALL_MAX_ABS_NORMAL_DOT_DOWN = 0.08715574274765817

    /** Floor/table: `|n · g| >= cos(5 deg)`. */
    const val FLOOR_MIN_ABS_NORMAL_DOT_DOWN = 0.9961946980917455

    /**
     * Minimum cosine between the plane normal and every object/stick ray: sin(5 deg), the same
     * grazing limit as the rectangle candidate. Rays below it, or on both depth sides of the
     * plane, make the projection unusable.
     */
    const val MIN_RAY_PLANE_COS = RectangleTolerances.MIN_RAY_PLANE_COS

    /**
     * Maximum relative disagreement between the stick's length- and width-derived scales on a
     * candidate plane. Half-pixel marking of a 10 px wide stick box gives about 5%, so 10%
     * keeps a 2x margin. It catches plane errors whose foreshortening exceeds that, e.g. a wall
     * yawed 30 deg but assumed to face the camera gives `1 - cos 30 = 13%`; smaller azimuth
     * errors are NOT detectable, which is why the wall-azimuth assumption also caps confidence.
     */
    const val MAX_STICK_DISAGREEMENT = 0.10

    /** Confidence factor left when a measured quantity sits exactly at its limit. */
    const val FACTOR_AT_LIMIT = 0.1

    /** Upper confidence bound for intrinsics that are approximate (never exact evidence). */
    const val APPROXIMATE_CALIBRATION_CAP = 0.6

    /** Upper confidence bound when the intrinsics were not established for these pixels. */
    const val UNAVAILABLE_CALIBRATION_CAP = 0.3

    /** Upper confidence bound when no valid gravity could verify the wall/floor selection. */
    const val UNVERIFIED_SURFACE_CAP = 0.6

    /** Upper confidence bound when the stick width is unknown and its shape is not checked. */
    const val UNCHECKED_REFERENCE_CAP = 0.6

    /** Upper confidence bound for a wall whose azimuth is only assumed (below "Medium"). */
    const val ASSUMED_AZIMUTH_CAP = 0.35

    /** Accepted `|g|` range for a gravity vector before it is normalized. */
    const val MIN_GRAVITY_NORM = 0.5
    const val MAX_GRAVITY_NORM = 2.0
}
