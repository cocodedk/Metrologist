# Plan 3 — Gravity Solver + Auto-Selector + Diagnostics (contract)

**Goal:** Complete the **hybrid** engine: add a gravity-based plane solver, a selector that
auto-picks between the rectangle and gravity solvers, and a diagnostics summary. Pure Kotlin,
package `com.cocode.measureapp.geometry`, **100% TDD coverage** (line + branch + method via
JaCoCo, plus an adversarial manual audit). Builds on Plan 2; do not break existing tests.

## Key insight (simplifies everything)

`projectToPlane` places 3D points using only the plane **normal** (`X = ray / (normal·ray)`).
The in-plane basis `(e1, e2)` is just an orthonormal coordinate choice on that plane, so all
measurements (distances, area, angles) are **invariant** to the basis. Therefore the gravity
solver only has to get the **normal** right; `e1`,`e2` may be any orthonormal in-plane pair.

## Camera frame convention

Camera frame is **x-right, y-down, z-forward** (matches Plan 2 `SyntheticScene`:
`pixel = K·((R·X + t)/z)`, depth `z > 0`). `gravity` is the unit camera-frame vector pointing
along world **down**. For a perfectly level camera, `gravity = (0, 1, 0)`. The optical axis is
`Vec3(0.0, 0.0, 1.0)`.

## New types

- `enum class SurfaceOrientation { VERTICAL, HORIZONTAL }`
- `Selection(solution: PlaneSolution, reason: String)`
- `MeasurementDiagnostics(solver: SolverKind, confidence: Double, cameraTiltDeg: Double, scale: Double, scaleAgreement: Double)`
- Extend `EngineResult` with a trailing `diagnostics: MeasurementDiagnostics? = null` (default
  keeps all Plan 2 positional constructions valid).

## Components

### GravitySolver — `object GravitySolver { fun solve(gravity: Vec3, orientation: SurfaceOrientation): PlaneSolution }`

`worldUp = (gravity * -1.0).normalized()`; `axis = Vec3(0.0, 0.0, 1.0)` (optical axis).

- **VERTICAL** (wall): the plane contains `worldUp`, so the normal is horizontal and chosen to
  face the camera. `candidate = axis * -1.0`;
  `nHoriz = candidate - worldUp * candidate.dot(worldUp)` (remove the vertical component).
  If `nHoriz.norm() < Tolerances.PROJ_EPS` → degenerate (camera looking straight up/down the
  wall): return a frame with a fallback horizontal normal and `confidence = 0.0`.
  Else `normal = nHoriz.normalized()`, `e2 = worldUp`, `e1 = worldUp.cross(normal).normalized()`,
  `confidence = nHoriz.norm().coerceIn(0.0, 1.0)` (lower as the view skims along the wall).
- **HORIZONTAL** (floor): `normal = worldUp`.
  `fwd = axis - worldUp * axis.dot(worldUp)`;
  `e2 = if (fwd.norm() > Tolerances.PROJ_EPS) fwd.normalized() else stableHorizontal(worldUp)`;
  `e1 = normal.cross(e2).normalized()`;
  `confidence = abs(axis.dot(worldUp)).coerceIn(0.0, 1.0)` (higher when looking down at the floor).
  `stableHorizontal(up)` = any fixed vector minus its `up` component, normalized (deterministic).

Return `PlaneSolution(PlaneFrame(e1, e2, normal), SolverKind.GRAVITY, confidence)`. Validate the
returned frame is orthonormal in tests.

### SolverSelector — `object SolverSelector { fun select(rectangle: PlaneSolution?, gravity: PlaneSolution?, minRectangleConfidence: Double = 0.15): Selection }`

- If `rectangle != null` and `rectangle.confidence >= minRectangleConfidence` → choose rectangle,
  reason "rectangle: corners well-conditioned".
- Else if `gravity != null` and `gravity.confidence > 0.0` → choose gravity, reason
  "fallback: rectangle null/low-confidence (<conf>)".
- Else → choose whichever is non-null with the higher confidence (rectangle preferred on ties);
  reason notes low overall confidence. (Both-null is impossible: gravity always returns a value.)

### Hybrid engine — `fun MetrologyEngine.measureHybrid(corners, stick, k, profile, gravity: Vec3, orientation: SurfaceOrientation): EngineResult`

Refactor the Plan 2 `measure` so the "given a usable `PlaneSolution`, project + scale + measure +
build confidence" logic is a shared private function. Keep the existing `measure(...)` working
(rectangle-only) by delegating to it. `measureHybrid`:
`rect = RectangleSolver.solve(corners, k)`; `grav = GravitySolver.solve(gravity, orientation)`;
`sel = SolverSelector.select(rect, grav)`; run the shared logic with `sel.solution`; attach
`MeasurementDiagnostics(sel.solution.solver, confidence, cameraTiltDeg, scale, scaleAgreement)`.
`cameraTiltDeg = Math.toDegrees(asin((Vec3(0,0,1).dot((gravity*-1.0).normalized())).coerceIn(-1.0,1.0)))`
(camera pitch above horizontal; document sign).

## Test strategy (TDD)

- Unit tests per component with hand-computed expectations (level camera VERTICAL gives
  `normal=(0,0,-1)`, `e2=(0,-1,0)`, `e1=(1,0,0)`; HORIZONTAL top-down gives `normal=worldUp`;
  degenerate cases hit the `confidence=0`/fallback branches; selector picks rectangle vs gravity
  across the threshold; both-low path).
- **Gravity oracle:** extend `SyntheticScene` to also expose the camera-frame gravity for a given
  pose (`gravityCam = R * worldDown`, `worldDown = (0,1,0)` in world). For walls that **face the
  camera**, `GravitySolver` + the pipeline must recover `width`/`height`/`area` within **≤2%**
  (gravity is intentionally less accurate than the rectangle solver's 0.5%). Add a case where the
  wall is at an oblique **azimuth** and assert the gravity solver is materially less accurate
  (documents the faces-camera assumption) while the rectangle solver stays accurate.
- **Selector oracle:** on a clean oblique rectangle scene, `measureHybrid` selects `RECTANGLE`;
  on a fronto-parallel scene (rectangle returns null), it selects `GRAVITY` and still produces a
  finite measurement.

## Coverage requirement

100% line + branch + method on all new pure-Kotlin code (JaCoCo `jacocoTestReport`, generated
data-class getters exercised by direct reads), confirmed by an adversarial manual audit. Files
under ~200 lines. No Android imports.
