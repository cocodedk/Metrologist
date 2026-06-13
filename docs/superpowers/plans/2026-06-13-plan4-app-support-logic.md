# Plan 4 — Pure App-Support Logic (contract)

**Goal:** The pure-Kotlin logic the thin UI will call: corner auto-ordering, unit
conversion/formatting, and plain-language confidence/diagnostics text. **No Android
dependencies.** New package `com.cocode.measureapp.core` (imports `geometry` types as needed).
**100% TDD coverage** (line + branch + method via JaCoCo + adversarial manual audit). Every
file under ~200 lines. The existing `geometry` package and its 100% coverage must stay green.

App convention: lengths flow through the engine in **meters** (the stick's `totalLength` is in
meters, so `MeasurementResult` values are meters and areas are square meters). The `core` layer
converts/formats for display.

## Components

### CornerOrdering — `object CornerOrdering { fun order(points: List<Vec2>): List<Vec2> }`

Take 4 image points tapped in arbitrary order and return them as `[TL, TR, BR, BL]` (clockwise,
image coordinates with y pointing **down**). `require(points.size == 4)`.

Order by sorting the 4 points around their centroid by `atan2(y - cy, x - cx)` (a guaranteed
clockwise cycle in y-down image coordinates), then rotate the cycle to start at the top-left-most
vertex (smallest `x + y`; ties broken by smallest `y`, then smallest `x`). Return `[TL, TR, BR, BL]`.
A simple sum/diff rule (`TL = min(x+y)`, `BR = max(x+y)`, `TR = min(y-x)`, `BL = max(y-x)`) is
**not** used: it collapses two corners on diamond-oriented (~45°) quads and is order-dependent on
ties — the centroid-angle method is tie-stable and keeps the 4 corners distinct.

Degenerate input (duplicate/collinear points that do not yield 4 distinct corners) throws
`IllegalArgumentException` rather than silently returning a duplicated corner.

Tests: every permutation/shuffle of a known rectangle's corners returns the same `[TL,TR,BR,BL]`;
a rotated/perspective quad; a non-axis-aligned quad; a diamond-oriented (~45°) quad returns 4
distinct corners; a quad with a 45° edge stays permutation-invariant; a strongly sheared convex
quad keeps 4 distinct corners; degenerate input throws; `size != 4` throws.

### Units — `enum class LengthUnit { METERS, CENTIMETERS, FEET_INCHES }` + `object Units`

- `fun formatLength(meters: Double, unit: LengthUnit): String`
  - METERS: `"%.2f m"` (e.g. `1.0 -> "1.00 m"`).
  - CENTIMETERS: `meters * 100`, `"%.1f cm"` (e.g. `1.0 -> "100.0 cm"`).
  - FEET_INCHES: `totalInches = meters / 0.0254`; `feet = floor(totalInches / 12)`;
    `inches = round(totalInches - feet * 12)`; **if `inches == 12` carry** to `feet + 1`, `inches = 0`;
    format `"<feet>' <inches>\""` (e.g. `1.0 -> "3' 3\""`).
- `fun formatArea(squareMeters: Double, unit: LengthUnit): String`
  - METERS: `"%.2f m²"`; CENTIMETERS: `* 10000` `"%.0f cm²"`; FEET_INCHES: `* 10.7639` `"%.2f ft²"`.

Tests: each unit's length + area formatting against hand-computed values; the inch-rounding
**carry** branch (a value whose inches round to 12 rolls to the next foot); zero; a large value.

### DiagnosticsText — `object DiagnosticsText`

Turn a `MeasurementDiagnostics` (from the engine) into plain-language UI text. Pure string logic.

- `fun confidenceLabel(confidence: Double): String` — `>= 0.7 -> "High confidence"`,
  `>= 0.4 -> "Medium confidence"`, else `"Low confidence"`.
- `fun caveats(d: MeasurementDiagnostics): List<String>` — append a caveat when each condition holds:
  - `d.confidence < 0.4` → "Low confidence — corners may not be square or the angle is too shallow; try a moderate angle."
  - `d.scaleAgreement > 0.1` → "Stick band spacing disagrees — make sure the stick lies flat on the surface."
  - `d.solver == SolverKind.GRAVITY` → "Used the tilt-sensor fallback; accuracy is lower than the rectangle method."
  - `abs(d.cameraTiltDeg) > 60.0` → "Camera is tilted steeply; re-shoot closer to level for best accuracy."
  - Returns an empty list when none apply.

Tests: each threshold boundary for `confidenceLabel` (e.g. 0.7, 0.4, just below); each caveat
toggled on and off independently; a clean diagnostics object yields an empty caveat list; a
worst-case object yields all four caveats.

## Coverage requirement

100% line + branch + method on the new `com.cocode.measureapp.core` package (JaCoCo
`jacocoTestReport`; exercise generated data-class/enum members via direct reads), confirmed by an
adversarial manual audit. The `geometry` package must remain at 100%. Verify the JaCoCo task
reports `core` (extend its source/class scope if needed so `core` appears in the report).
