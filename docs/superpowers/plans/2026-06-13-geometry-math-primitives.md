# Geometry Math Primitives Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the pure-Kotlin math substrate (2D/3D vectors, 3×3 matrices, camera intrinsics, homogeneous line/intersection helpers) that the metrology solvers stand on.

**Architecture:** All code lives in package `com.cocode.measureapp.geometry`, has **no Android dependencies**, and is exercised by plain JVM unit tests in `app/src/test`. Each file has one responsibility and stays well under 200 lines.

**Tech Stack:** Kotlin, JUnit4 (already on the classpath via the scaffold). Run tests with Gradle's `:app:testDebugUnitTest`.

**Plan 1 of 7** — see `docs/superpowers/specs/2026-06-13-measure-app-design.md` for the full design and the plan sequence.

---

### Task 1: Vectors (`Vec2`, `Vec3`)

**Files:**
- Create: `app/src/main/java/com/cocode/measureapp/geometry/Vec.kt`
- Test: `app/src/test/java/com/cocode/measureapp/geometry/VecTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.cocode.measureapp.geometry

import org.junit.Assert.assertEquals
import org.junit.Test

class VecTest {
    @Test fun crossOfUnitXAndUnitYIsUnitZ() {
        val r = Vec3(1.0, 0.0, 0.0).cross(Vec3(0.0, 1.0, 0.0))
        assertEquals(0.0, r.x, 1e-9)
        assertEquals(0.0, r.y, 1e-9)
        assertEquals(1.0, r.z, 1e-9)
    }

    @Test fun normalizedHasUnitLengthAndDirection() {
        val r = Vec3(3.0, 0.0, 4.0).normalized()
        assertEquals(1.0, r.norm(), 1e-9)
        assertEquals(0.6, r.x, 1e-9)
        assertEquals(0.8, r.z, 1e-9)
    }

    @Test fun vec2DistanceIsEuclidean() {
        assertEquals(5.0, Vec2(0.0, 0.0).distanceTo(Vec2(3.0, 4.0)), 1e-9)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.cocode.measureapp.geometry.VecTest"`
Expected: FAIL — `Vec2`/`Vec3` unresolved.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package com.cocode.measureapp.geometry

import kotlin.math.sqrt

data class Vec2(val x: Double, val y: Double) {
    operator fun plus(o: Vec2) = Vec2(x + o.x, y + o.y)
    operator fun minus(o: Vec2) = Vec2(x - o.x, y - o.y)
    fun dot(o: Vec2) = x * o.x + y * o.y
    fun norm() = sqrt(x * x + y * y)
    fun distanceTo(o: Vec2) = (this - o).norm()
}

data class Vec3(val x: Double, val y: Double, val z: Double) {
    operator fun plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
    operator fun times(s: Double) = Vec3(x * s, y * s, z * s)
    fun dot(o: Vec3) = x * o.x + y * o.y + z * o.z
    fun cross(o: Vec3) = Vec3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)
    fun norm() = sqrt(x * x + y * y + z * z)
    fun normalized(): Vec3 {
        val n = norm()
        require(n > 1e-12) { "cannot normalize zero-length vector" }
        return Vec3(x / n, y / n, z / n)
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.cocode.measureapp.geometry.VecTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/cocode/measureapp/geometry/Vec.kt \
        app/src/test/java/com/cocode/measureapp/geometry/VecTest.kt
git commit -m "feat(geometry): add Vec2/Vec3 with cross, dot, normalize"
```

---

### Task 2: 3×3 matrix (`Mat3`)

**Files:**
- Create: `app/src/main/java/com/cocode/measureapp/geometry/Mat3.kt`
- Test: `app/src/test/java/com/cocode/measureapp/geometry/Mat3Test.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.cocode.measureapp.geometry

import org.junit.Assert.assertEquals
import org.junit.Test

class Mat3Test {
    @Test fun inverseUndoesMultiplication() {
        val m = Mat3(2.0, 0.0, 1.0, 1.0, 1.0, 0.0, 0.0, 1.0, 1.0)
        val v = Vec3(1.0, 2.0, 3.0)
        val back = m.inverse() * (m * v)
        assertEquals(1.0, back.x, 1e-9)
        assertEquals(2.0, back.y, 1e-9)
        assertEquals(3.0, back.z, 1e-9)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.cocode.measureapp.geometry.Mat3Test"`
Expected: FAIL — `Mat3` unresolved.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package com.cocode.measureapp.geometry

import kotlin.math.abs

/** Row-major 3x3 matrix. */
data class Mat3(
    val m00: Double, val m01: Double, val m02: Double,
    val m10: Double, val m11: Double, val m12: Double,
    val m20: Double, val m21: Double, val m22: Double,
) {
    operator fun times(v: Vec3) = Vec3(
        m00 * v.x + m01 * v.y + m02 * v.z,
        m10 * v.x + m11 * v.y + m12 * v.z,
        m20 * v.x + m21 * v.y + m22 * v.z,
    )

    fun determinant(): Double =
        m00 * (m11 * m22 - m12 * m21) -
        m01 * (m10 * m22 - m12 * m20) +
        m02 * (m10 * m21 - m11 * m20)

    fun inverse(): Mat3 {
        val det = determinant()
        require(abs(det) > 1e-12) { "matrix not invertible" }
        val s = 1.0 / det
        return Mat3(
            (m11 * m22 - m12 * m21) * s, (m02 * m21 - m01 * m22) * s, (m01 * m12 - m02 * m11) * s,
            (m12 * m20 - m10 * m22) * s, (m00 * m22 - m02 * m20) * s, (m02 * m10 - m00 * m12) * s,
            (m10 * m21 - m11 * m20) * s, (m01 * m20 - m00 * m21) * s, (m00 * m11 - m01 * m10) * s,
        )
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.cocode.measureapp.geometry.Mat3Test"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/cocode/measureapp/geometry/Mat3.kt \
        app/src/test/java/com/cocode/measureapp/geometry/Mat3Test.kt
git commit -m "feat(geometry): add Mat3 with mat-vec product and inverse"
```

---

### Task 3: Camera intrinsics (`CameraIntrinsics`)

**Files:**
- Create: `app/src/main/java/com/cocode/measureapp/geometry/CameraIntrinsics.kt`
- Test: `app/src/test/java/com/cocode/measureapp/geometry/CameraIntrinsicsTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.cocode.measureapp.geometry

import org.junit.Assert.assertEquals
import org.junit.Test

class CameraIntrinsicsTest {
    @Test fun inverseMatrixMapsPrincipalPointToOpticalAxis() {
        val k = CameraIntrinsics(fx = 1000.0, fy = 1000.0, cx = 640.0, cy = 360.0)
        val ray = k.inverseMatrix() * Vec3(640.0, 360.0, 1.0)
        assertEquals(0.0, ray.x, 1e-9)
        assertEquals(0.0, ray.y, 1e-9)
        assertEquals(1.0, ray.z, 1e-9)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.cocode.measureapp.geometry.CameraIntrinsicsTest"`
Expected: FAIL — `CameraIntrinsics` unresolved.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package com.cocode.measureapp.geometry

/** Pinhole camera intrinsics, in pixels. */
data class CameraIntrinsics(
    val fx: Double,
    val fy: Double,
    val cx: Double,
    val cy: Double,
) {
    fun matrix() = Mat3(
        fx, 0.0, cx,
        0.0, fy, cy,
        0.0, 0.0, 1.0,
    )

    fun inverseMatrix() = matrix().inverse()
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.cocode.measureapp.geometry.CameraIntrinsicsTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/cocode/measureapp/geometry/CameraIntrinsics.kt \
        app/src/test/java/com/cocode/measureapp/geometry/CameraIntrinsicsTest.kt
git commit -m "feat(geometry): add CameraIntrinsics matrix/inverse"
```

---

### Task 4: Projective helpers (`Projective`)

**Files:**
- Create: `app/src/main/java/com/cocode/measureapp/geometry/Projective.kt`
- Test: `app/src/test/java/com/cocode/measureapp/geometry/ProjectiveTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.cocode.measureapp.geometry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProjectiveTest {
    @Test fun intersectionOfPerpendicularLines() {
        val vertical = Projective.lineThrough(Vec2(2.0, 0.0), Vec2(2.0, 5.0))
        val horizontal = Projective.lineThrough(Vec2(0.0, 3.0), Vec2(5.0, 3.0))
        val p = Projective.intersection(vertical, horizontal)!!
        assertEquals(2.0, p.x, 1e-9)
        assertEquals(3.0, p.y, 1e-9)
    }

    @Test fun parallelLinesHaveNoFiniteIntersection() {
        val l1 = Projective.lineThrough(Vec2(0.0, 0.0), Vec2(5.0, 0.0))
        val l2 = Projective.lineThrough(Vec2(0.0, 1.0), Vec2(5.0, 1.0))
        assertNull(Projective.intersection(l1, l2))
    }

    @Test fun vanishingPointOfConvergingEdges() {
        val vp = Projective.vanishingPoint(
            Vec2(0.0, 0.0), Vec2(10.0, 1.0),
            Vec2(0.0, 2.0), Vec2(10.0, 1.0),
        )!!
        assertEquals(10.0, vp.x, 1e-9)
        assertEquals(1.0, vp.y, 1e-9)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.cocode.measureapp.geometry.ProjectiveTest"`
Expected: FAIL — `Projective` unresolved.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package com.cocode.measureapp.geometry

import kotlin.math.abs

/** Homogeneous-coordinate helpers for image lines and their intersections. */
object Projective {
    /** Homogeneous line through two image points. */
    fun lineThrough(a: Vec2, b: Vec2): Vec3 =
        Vec3(a.x, a.y, 1.0).cross(Vec3(b.x, b.y, 1.0))

    /** Intersection of two homogeneous lines; null when parallel in the image. */
    fun intersection(l1: Vec3, l2: Vec3): Vec2? {
        val p = l1.cross(l2)
        if (abs(p.z) < 1e-9) return null
        return Vec2(p.x / p.z, p.y / p.z)
    }

    /**
     * Vanishing point of world-parallel edges (a1->a2) and (b1->b2).
     * Null when the edges appear parallel in the image (fronto-parallel view).
     */
    fun vanishingPoint(a1: Vec2, a2: Vec2, b1: Vec2, b2: Vec2): Vec2? =
        intersection(lineThrough(a1, a2), lineThrough(b1, b2))
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.cocode.measureapp.geometry.ProjectiveTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/cocode/measureapp/geometry/Projective.kt \
        app/src/test/java/com/cocode/measureapp/geometry/ProjectiveTest.kt
git commit -m "feat(geometry): add projective line/intersection/vanishing-point helpers"
```

---

## Self-Review

- **Spec coverage:** This plan delivers the reusable math primitives named in the spec's `geometry` module (vectors, matrices, intrinsics, vanishing points). The solvers, projection, scale, and measurements are Plan 2; they depend only on these types.
- **Placeholder scan:** None — every step has runnable code and an exact command.
- **Type consistency:** `Vec2`, `Vec3`, `Mat3`, `CameraIntrinsics`, `Projective` are used identically across tasks. `Mat3 * Vec3`, `CameraIntrinsics.inverseMatrix()`, and `Projective.vanishingPoint(...)` signatures match their definitions and the Plan 2 entry points.
- **Note for Plan 2:** the rectangle solver consumes `Projective.vanishingPoint(...)` and `CameraIntrinsics.inverseMatrix()`; `vanishingPoint` returning `null` (fronto-parallel) must be handled there as "fall back / low confidence."
