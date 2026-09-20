package com.cocode.measureapp.contracts

import com.cocode.measureapp.geometry.CameraIntrinsics
import com.cocode.measureapp.geometry.MeasurementOutcome
import com.cocode.measureapp.geometry.MeasurementFailureReason
import com.cocode.measureapp.geometry.MeasurementResult
import com.cocode.measureapp.geometry.Measurements
import com.cocode.measureapp.geometry.Vec2
import com.cocode.measureapp.geometry.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.round
import kotlin.math.sin

/** Rotations of camera-frame vectors (x right, y down, z forward), in degrees. */
object Rot {
    /** Camera pitched down by [deg]: its optical axis tips toward physical down. */
    fun pitch(deg: Double): (Vec3) -> Vec3 = { v ->
        val a = Math.toRadians(deg)
        Vec3(v.x, cos(a) * v.y - sin(a) * v.z, sin(a) * v.y + cos(a) * v.z)
    }

    fun yaw(deg: Double): (Vec3) -> Vec3 = { v ->
        val a = Math.toRadians(deg)
        Vec3(cos(a) * v.x + sin(a) * v.z, v.y, -sin(a) * v.x + cos(a) * v.z)
    }

    fun roll(deg: Double): (Vec3) -> Vec3 = { v ->
        val a = Math.toRadians(deg)
        Vec3(cos(a) * v.x - sin(a) * v.y, sin(a) * v.x + cos(a) * v.y, v.z)
    }
}

/** Object and stick corners in plane coordinates (metres); [oracle] is the true measurement. */
data class Planar(val obj: List<Vec2>, val stick: List<Vec2>) {
    val oracle: MeasurementResult get() = Measurements.compute(obj)
}

/** A plane placed in a camera frame, with physical [down] in that same frame. */
data class Placement(val origin: Vec3, val e1: Vec3, val e2: Vec3, val down: Vec3) {
    /** The camera turned by [f]: every point and direction is expressed in the new camera frame. */
    fun moved(f: (Vec3) -> Vec3) = Placement(f(origin), f(e1), f(e2), f(down))

    fun at(p: Vec2): Vec3 = origin + e1 * p.x + e2 * p.y

    /** Exact pinhole projection of plane points through [k]. */
    fun pixels(points: List<Vec2>, k: CameraIntrinsics): List<Vec2> = points.map {
        val c = at(it)
        Vec2(k.fx * c.x / c.z + k.cx, k.fy * c.y / c.z + k.cy)
    }
}

/** Synthetic targets shared by the contract tests. */
object Targets {
    /** Wall camera: 1280 x 960 buffer, f = 1000 px. */
    val K_WALL = CameraIntrinsics(1000.0, 1000.0, 640.0, 480.0)

    /** Floor camera: f equals the long edge, so the field-of-view fallback is exact. */
    val K_FLOOR = CameraIntrinsics(1280.0, 1280.0, 640.0, 480.0)
    const val W = 1280
    const val H = 960

    /** 1.2 x 0.8 m rectangle with a 0.5 x 0.05 m stick below it (plane v grows downward). */
    val WALL_RECT = Planar(
        listOf(Vec2(-0.6, -0.5), Vec2(0.6, -0.5), Vec2(0.6, 0.3), Vec2(-0.6, 0.3)),
        listOf(Vec2(-0.25, 0.4), Vec2(0.25, 0.4), Vec2(0.25, 0.45), Vec2(-0.25, 0.45)),
    )
    val WALL = Placement(Vec3(0.0, 0.0, 3.0), Vec3(1.0, 0.0, 0.0), Vec3(0.0, 1.0, 0.0), Vec3(0.0, 1.0, 0.0))

    /** Floor 1.4 m below a level camera; plane v grows away from the camera. */
    val FLOOR = Placement(Vec3(0.0, 1.4, 2.4), Vec3(1.0, 0.0, 0.0), Vec3(0.0, 0.0, 1.0), Vec3(0.0, 1.0, 0.0))

    /** The floor seen by a camera pitched 35 degrees down and rolled 10 degrees. */
    val TILTED_FLOOR = FLOOR.moved { Rot.roll(10.0)(Rot.pitch(35.0)(it)) }

    private val FLOOR_STICK = listOf(Vec2(-0.25, -0.6), Vec2(0.25, -0.6), Vec2(0.25, -0.55), Vec2(-0.25, -0.55))
    val FLOOR_RECT = Planar(listOf(Vec2(-0.4, 0.5), Vec2(0.4, 0.5), Vec2(0.4, -0.3), Vec2(-0.4, -0.3)), FLOOR_STICK)

    /** A clearly non-rectangular floor quad: only the gravity plane can measure it. */
    val FLOOR_QUAD = Planar(listOf(Vec2(-0.4, 0.5), Vec2(0.25, 0.5), Vec2(0.45, -0.4), Vec2(-0.35, -0.25)), FLOOR_STICK)
}

/** Assertions on engine outputs. */
object Expect {
    fun near(expected: Double, actual: Double, rel: Double, what: String) = assertTrue(
        "$what: expected $expected, got $actual (relative $rel)",
        abs(actual - expected) <= rel * max(abs(expected), 1e-12),
    )

    /** Same measurement, corner by corner. */
    fun sameResult(e: MeasurementResult, a: MeasurementResult, rel: Double = 1e-6, what: String = "") {
        near(e.width, a.width, rel, "$what width")
        near(e.height, a.height, rel, "$what height")
        near(e.area, a.area, rel, "$what area")
        near(e.diagonal, a.diagonal, rel, "$what diagonal")
        assertEquals("$what angle count", 4, a.cornerAngles.size)
        e.cornerAngles.zip(a.cornerAngles).forEachIndexed { i, (x, y) -> near(x, y, rel, "$what angle $i") }
    }

    /** Same physical shape whatever corner the canonical image order starts from. */
    fun sameShape(e: MeasurementResult, a: MeasurementResult, rel: Double = 1e-6, what: String = "") {
        near(e.area, a.area, rel, "$what area")
        near(e.diagonal, a.diagonal, rel, "$what diagonal")
        near(minOf(e.width, e.height), minOf(a.width, a.height), rel, "$what short side")
        near(maxOf(e.width, e.height), maxOf(a.width, a.height), rel, "$what long side")
        e.cornerAngles.sorted().zip(a.cornerAngles.sorted()).forEach { (x, y) -> near(x, y, rel, "$what angle") }
    }

    fun vec(e: Vec3, a: Vec3, tol: Double, what: String) =
        assertTrue("$what: expected $e, got $a", (a - e).norm() <= tol)

    fun success(o: MeasurementOutcome?): MeasurementOutcome.Success {
        assertTrue("expected a success, got $o", o is MeasurementOutcome.Success)
        return o as MeasurementOutcome.Success
    }

    fun failure(o: MeasurementOutcome?, reason: MeasurementFailureReason): MeasurementOutcome.Failure {
        assertTrue("expected a $reason failure, got $o", o is MeasurementOutcome.Failure)
        assertEquals((o as MeasurementOutcome.Failure).reason, reason)
        return o
    }

    /** Presenter rounding of one corner angle, as shown on Results. */
    fun angleText(deg: Double): String = "${round(deg * 10) / 10.0}°"
}
