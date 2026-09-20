package com.cocode.measureapp.geometry.rectangle

import com.cocode.measureapp.geometry.CameraIntrinsics
import com.cocode.measureapp.geometry.ContractScenes
import com.cocode.measureapp.geometry.SceneRotations
import com.cocode.measureapp.geometry.SyntheticScene
import com.cocode.measureapp.geometry.Vec2
import com.cocode.measureapp.geometry.Vec3

/**
 * Inputs that must yield explicit rectangle-candidate rejections rather than exceptions or
 * fabricated planes. Invoked from `VanishingPointContractTest`, which asserts the reasons.
 */
object RectangleRejectionFixtures {
    /** Finite, positive intrinsics whose `K` is not safely invertible or whose `K⁻¹` overflows. */
    val unusableIntrinsics: List<CameraIntrinsics> = listOf(
        // det = fx * fy = 1e-14 is below the Mat3.inverse guard, yet isUsable() is true.
        CameraIntrinsics(1e-7, 1e-7, 1000.0, 750.0),
        // det = 1e400 overflows to infinity; the naive inverse collapses to zero/NaN entries.
        CameraIntrinsics(1e200, 1e200, 1000.0, 750.0),
        // -cx / fx overflows to -infinity inside K⁻¹.
        CameraIntrinsics(1e-3, 1e-3, 1e308, 750.0),
    )

    /** Finite corner coordinates whose homogeneous edge lines overflow. */
    val overflowingCorners: List<Vec2> =
        ContractScenes.wall(SceneRotations.yawPitch(30.0, 0.0)).cornerPixels.map { it * 1e200 }

    /**
     * Self-crossing quad (under [ContractScenes.K]) with nonzero, distinct vanishing directions
     * whose top corner rays lie on the opposite side of the recovered plane from the bottom
     * ones: width VP at infinity along x, height VP at (1000, 833.3), normal ∝ (0, -1, 1/12);
     * top rays give cosine +1/3 and bottom rays -1/6, so no single positive depth exists.
     */
    val crossedQuad: List<Vec2> =
        listOf(Vec2(400.0, 500.0), Vec2(1600.0, 500.0), Vec2(700.0, 1000.0), Vec2(1300.0, 1000.0))

    /**
     * The contract wall yawed 60 degrees with the camera only 0.5 m from its centre: the right
     * half lies behind the camera (depth -0.37 m) while the left half is in front (1.37 m).
     * Pinhole projection of the rear corners flips them, so the marks straddle the plane.
     */
    val wallBehindCamera: SyntheticScene = SyntheticScene(
        w = 2.0, h = 1.0, r = SceneRotations.yaw(60.0), t = Vec3(0.0, 0.0, 0.5),
        k = ContractScenes.K, l = 1.0, sw = 0.04,
    )
}
