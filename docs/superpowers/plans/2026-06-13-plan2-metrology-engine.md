# Plan 2 — Rectangle Metrology Engine (contract)

**Goal:** A pure-Kotlin engine that turns marked image points + camera intrinsics + a
known stick length into real-world measurements with a confidence score, using the
**rectangle solver**. No Android dependencies. Package `com.cocode.measureapp.geometry`.
**Every new line and branch of pure-Kotlin code must be covered by unit tests (TDD).**

This builds on the Plan 1 primitives (`Vec2`, `Vec3`, `Mat3`, `CameraIntrinsics`,
`Projective`). The gravity solver + auto-selector are Plan 3 (a later workflow); design
interfaces so they slot in without rework.

## Foundation fixes first (from Plan 1 review)

- `Vec2.times(s: Double): Vec2` — scalar multiply (parity with `Vec3`).
- `Mat3.times(o: Mat3): Mat3` — matrix–matrix product (for composing transforms).
- `Tolerances` (top-level `object` or consts): `NORM_EPS = 1e-12` (unit-scale guards),
  `PROJ_EPS = 1e-9` (image-scale parallel/degeneracy). Refactor existing literals in
  `Vec.kt`, `Mat3.kt`, `Projective.kt` to reference these. Keep all existing tests green.

## Data model (new, pure data classes / enums)

- `StickProfile(totalLength: Double, bandCount: Int = 4)` — equal bands; `totalLength` is
  in the user's chosen real unit (engine is unit-agnostic; outputs are in the same unit).
- `PlaneFrame(e1: Vec3, e2: Vec3, normal: Vec3)` — orthonormal camera-frame triad; `e1`,`e2`
  span the plane, `normal` is perpendicular. (Validate orthonormality in tests.)
- `enum SolverKind { RECTANGLE, GRAVITY }`
- `PlaneSolution(frame: PlaneFrame, solver: SolverKind, confidence: Double)` — `confidence`
  in `[0,1]`.
- `ScaleResult(scale: Double, agreement: Double)` — `agreement` = max relative deviation
  among per-segment scale estimates (0 = perfect agreement).
- `MeasurementResult(width, height, area, diagonal: Double, cornerAngles: List<Double>)` —
  lengths in the stick's unit; `cornerAngles` in degrees, one per corner.
- `EngineResult(measurement: MeasurementResult, solution: PlaneSolution, scale: ScaleResult,
  confidence: Double)` — overall `confidence` combines solver confidence and scale agreement.

## Corner ordering convention

Input corners are a `List<Vec2>` of size 4 in clockwise order starting top-left:
`[TL, TR, BR, BL]`. Document and validate (reject other sizes).

## Functions / components

- `projectToPlane(points: List<Vec2>, k: CameraIntrinsics, frame: PlaneFrame): List<Vec2>`
  For each pixel `p`: `ray = k.inverseMatrix() * Vec3(p.x, p.y, 1.0)`;
  `denom = frame.normal.dot(ray)` (guard `abs(denom) > PROJ_EPS`);
  `X = ray * (1.0 / denom)`; return `Vec2(frame.e1.dot(X), frame.e2.dot(X))`.
  Result is metric **up to scale** (plane assumed at `normal·X = 1`).

- `RectangleSolver.solve(corners: List<Vec2>, k: CameraIntrinsics): PlaneSolution?`
  `vp1 = Projective.vanishingPoint(TL,TR, BL,BR)`; `vp2 = vanishingPoint(TL,BL, TR,BR)`.
  If either `null` → return `null` (caller treats as low confidence / fallback).
  `d1 = (k.inverseMatrix() * Vec3(vp1.x, vp1.y, 1.0)).normalized()`; likewise `d2`.
  `normal = d1.cross(d2).normalized()`; `e1 = d1`; `e2 = normal.cross(e1).normalized()`.
  `confidence` from orthogonality `1 - abs(d1.dot(d2))` (clamped to `[0,1]`).

- `ScaleSolver.solve(stickMetric: List<Vec2>, profile: StickProfile): ScaleResult`
  Consecutive distances `d_i`. With 5 points (2 ends + 3 joints) → 4 equal segments, each
  real length `totalLength / bandCount`. With 2 points (ends only) → 1 segment of length
  `totalLength`. Per-segment `est_i = realLen_i / d_i`; `scale = median(est)`;
  `agreement = max(|est_i - scale|) / scale` (0.0 when one segment).

- `Measurements.compute(corners: List<Vec2>): MeasurementResult`
  Corners are already real-scaled metric (TL,TR,BR,BL). `width` = mean(|TR-TL|, |BR-BL|);
  `height` = mean(|BL-TL|, |BR-TR|); `area` = shoelace; `diagonal` = mean(|BR-TL|, |BL-TR|);
  `cornerAngles[i]` = interior angle at corner i between its two adjacent edges, in degrees.

- `MetrologyEngine.measure(corners, stick: List<Vec2>, k, profile): EngineResult`
  `solution = RectangleSolver.solve(corners, k)`; if `null`, return an `EngineResult` with
  `confidence = 0.0` and a RECTANGLE solution flagged unusable (Plan 3 adds gravity fallback).
  Project corners + stick to up-to-scale metric via `solution.frame`. `ScaleSolver` on the
  stick metric → real scale. Multiply corner metric by `scale`. `Measurements.compute`.
  Overall `confidence = solution.confidence * (1 - min(scale.agreement, 1.0))`.

## Test strategy (TDD — this is the correctness backbone)

- **Per-function unit tests** with hand-computed expectations where feasible (e.g.
  `projectToPlane` on a fronto-parallel frame; `ScaleSolver` median/agreement; shoelace area;
  corner angles of a known rectangle = 90°).
- **Synthetic ground-truth oracle** (`SyntheticScene` test helper): given a real wall `W×H`,
  a camera pose `(R, t)` placing it in front of the camera, intrinsics `K`, and a stick of
  known length lying on the wall, **project** all 3D points to image pixels
  (`pixel = K * (Xc / Xc.z)`). Feed the resulting pixels to `MetrologyEngine.measure` and
  assert recovered `width`, `height`, `area` are within **≤0.5%** of truth. Cover poses:
  frontal-ish, oblique-yaw, oblique-pitch, combined tilt. Include a near-fronto-parallel
  case that exercises the `vanishingPoint == null` / low-confidence branch.
- **Branch coverage:** every `if`/`?:`/guard must have a test (null vanishing point, 2-point
  vs 5-point stick, non-square corners lowering confidence, denom guard in `projectToPlane`).

## Coverage requirement

100% line + branch coverage on all new pure-Kotlin code, verified by **JaCoCo** (configure
exclusions for compiler-generated `data class` members: `copy`, `component*`, `equals`,
`hashCode`, `toString`) **and** an adversarial manual audit mapping each branch to a test.
Keep every source file under ~200 lines.
