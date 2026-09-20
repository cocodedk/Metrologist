package com.cocode.measureapp.geometry.rectangle

/**
 * Numeric thresholds of the rectangle plane-candidate solver. They reject only numeric
 * degeneracy and unstable conditioning; whether a candidate is ELIGIBLE (rectangle
 * consistency, wall/floor agreement) is decided by the solver-selection node, never here.
 */
object RectangleTolerances {
    /**
     * Relative zero test for homogeneous cross products. Lines are unit-normalised, so a
     * cross product's norm is the sine between them: coincident lines give ~1e-16, while two
     * distinct image-parallel lines even 1 px apart give >= 1e-8 at 4k resolutions, four
     * orders of magnitude above this threshold.
     */
    const val ZERO_HOMOGENEOUS = 1e-12

    /**
     * Classification only: `|w| <= this * |(x, y)|` labels a vanishing point as at infinity.
     * Nothing is ever divided by `w`, so this threshold cannot cause a rejection.
     */
    const val INFINITY_RELATIVE = 1e-9

    /** Minimum sine between the two back-projected edge directions (coincident directions). */
    const val MIN_DIRECTION_SIN = 1e-9

    /**
     * Minimum cosine between the plane normal and any corner ray: sin(5 deg). Below it the view
     * grazes the plane and depth along the ray amplifies any normal error by more than 11x,
     * so the projection is numerically unusable for metrology.
     */
    const val MIN_RAY_PLANE_COS = 0.08715574274765817

    /** Assumed marking uncertainty per image coordinate, in image pixels. */
    const val MARK_UNCERTAINTY_PX = 0.5

    /**
     * Maximum first-order worst-case relative change of the rectified aspect ratio when each
     * of the 8 corner coordinates is moved by [MARK_UNCERTAINTY_PX]. Above 10% the recovered
     * shape cannot be told apart from marking noise, so a visually square quad must not be
     * reported as accurate. Well-imaged targets (a 2 x 1 m wall at 4 m, f = 1000 px, yaw up
     * to 60 deg) stay near 1-3%, leaving margin for the 2% half-pixel accuracy requirement.
     */
    const val MAX_HALF_PIXEL_ASPECT_SENSITIVITY = 0.10
}
