# Plan 5 — OpenCV Stick Detection with Extracted Pure Logic (contract)

**Goal:** Auto-detect the red-white-red-white stick. Split into:
1. **Pure-Kotlin geometry** (package `com.cocode.measureapp.stick`, no Android) — fit the stick
   axis, order points, validate the band pattern, produce the engine's 5 ordered points +
   confidence. **100% TDD coverage** (line+branch+method via JaCoCo + manual audit).
2. **Thin OpenCV adapter** (package `com.cocode.measureapp.detect`, Android) — HSV pixel
   segmentation only; delegates ALL geometry to the pure logic. **Compile-verified only**
   (`assembleDebug`); native runtime needs a device.

Lengths/points are in image pixels. The engine consumes the stick as `List<Vec2>` of
`bandCount + 1` points (5 for a 4-band stick), evenly spaced end-to-end, which `ScaleSolver`
already expects.

## Pure logic — package `com.cocode.measureapp.stick`

### Axis
`data class Axis(val origin: Vec2, val direction: Vec2)` (direction is unit). Methods:
- `fun project(p: Vec2): Double = (p - origin).dot(direction)`
- `fun pointAt(t: Double): Vec2 = origin + direction * t`
- `fun perpendicularDistance(p: Vec2): Double` = `((p - origin) - direction * project(p)).norm()`

### PrincipalAxis — `object PrincipalAxis { fun fit(points: List<Vec2>): Axis }`
`require(points.size >= 2)`. Centroid = mean. Population covariance `Sxx, Sxy, Syy`.
Largest eigenvalue `l1 = (Sxx+Syy)/2 + sqrt(((Sxx-Syy)/2)^2 + Sxy^2)`. Direction:
- if `abs(Sxy) > Tolerances.NORM_EPS` → `Vec2(l1 - Syy, Sxy).normalized()`
- else if `Sxx >= Syy` → `Vec2(1.0, 0.0)` else `Vec2(0.0, 1.0)`.
Return `Axis(centroid, direction)`.

### StickPoints
`data class StickPoints(val points: List<Vec2>, val confidence: Double)` (confidence in [0,1]).

### StickAssembler — `object StickAssembler`
`fun assemble(stickPoints: List<Vec2>, redPoints: List<Vec2>, bandCount: Int = 4, minConfidence: Double = 0.3): StickPoints?`
- `require(bandCount >= 2)`. If `stickPoints.size < 2` → return null.
- `axis = PrincipalAxis.fit(stickPoints)`. `ts = stickPoints.map { axis.project(it) }`;
  `tMin = ts.min()`, `tMax = ts.max()`, `len = tMax - tMin`. If `len < Tolerances.NORM_EPS` → null.
- **Five points:** for `i in 0..bandCount`, `t = tMin + len * i / bandCount`; point = `axis.pointAt(t)`.
  Ordered end to end.
- **Collinearity** in [0,1]: `perpRms = sqrt(mean(stickPoints.map { axis.perpendicularDistance(it)^2 }))`;
  `collinearity = (1.0 - (perpRms / (len * 0.1))).coerceIn(0.0, 1.0)` (high when points hug the axis
  to within ~10% of its length).
- **Red-pattern score** in [0,1]: normalize each red point `u = (axis.project(it) - tMin) / len`;
  keep `u in [0,1]` (drop outliers). Alternating bands starting red: orientation-A red band
  indices = even `{0,2,...}`, orientation-B = odd `{1,3,...}`. `inBands(idxs)` = fraction of kept
  red `u` whose band index `floor(u*bandCount).coerceIn(0,bandCount-1)` is in `idxs`.
  `redScore = max(inBands(even), inBands(odd))`. If no usable red points → `redScore = 0.0`.
- `confidence = (collinearity * redScore).coerceIn(0.0, 1.0)`.
- If `confidence < minConfidence` → return null; else `StickPoints(fivePoints, confidence)`.

### Tests (TDD, 100%)
Dense points sampled along a known segment A→B with red points in the correct red bands →
5 evenly spaced points A..B, high confidence. Orientation-flipped red → still high (max of A/B).
Noisy/curved points → lower collinearity. Empty red → null. Degenerate (all-equal / <2 points)
→ null. `bandCount < 2` throws. Axis fit: points on `y = 2x` give the right direction; the
`Sxy ≈ 0` axis-aligned branches (horizontal and vertical point clouds); `< 2` points throws.
Cover every branch including both orientation arms and the `minConfidence` cutoff (just-below
returns null, just-above returns points).

## Thin OpenCV adapter — package `com.cocode.measureapp.detect` (Android, compile-only)

- Keep `StickDetector` interface but change its result to the pure type:
  `fun detect(image: Bitmap): StickPoints?`. Update `DeferredStickDetector` to return null.
  Remove the old `StickDetection` type (replaced by `stick.StickPoints`).
- `class OpenCvStickDetector(...) : StickDetector`:
  - `Utils.bitmapToMat`; `cvtColor` RGBA→RGB→HSV.
  - Red mask = two hue ranges (≈0–10 and ≈160–179, high S/V) OR-ed; white mask = low S, high V;
    `stickMask = red OR white`.
  - Largest contour of `stickMask` → sample/downsample its points to `stickPoints: List<Vec2>`.
  - `Core.findNonZero` on the red mask → downsample to `redPoints: List<Vec2>`.
  - `return StickAssembler.assemble(stickPoints, redPoints)`. Wrap in try/catch → null on any error.
  - Release `Mat`s.
- Add `OpenCVLoader.initLocal()` once at app start (MainActivity), guarded; if it fails, detection
  simply returns null (manual marking still works).

## Wiring (compile-only)
`MarkScreen` takes a `StickDetector` (default `DeferredStickDetector`; the app passes
`OpenCvStickDetector`). On first composition, run `detector.detect(bitmap)` on a background
dispatcher; if it returns points above threshold, pre-fill the stick markers (still editable) and
show "auto-detected — tap Reset to mark manually". Manual marking remains the guaranteed path.

## Coverage requirement
Package `com.cocode.measureapp.stick` at 100% line+branch+method (generated data-class members
exercised by direct reads). `geometry` and `core` remain at 100%. The `detect` OpenCV adapter is
compile-verified only and excluded from the coverage expectation. Files under ~200 lines.
