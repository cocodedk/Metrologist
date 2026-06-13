# Measure App — v1 Design Spec

**Date:** 2026-06-13
**Status:** Approved (brainstorming) — ready for implementation planning
**Package:** `com.cocode.measureapp` · Kotlin + Jetpack Compose · minSdk 24 / targetSdk 36

## Concept

Photograph a flat surface with a known-length red-white-red-white stick lying on it.
The app detects the stick, lets the user mark the surface corners, then reverses the
camera perspective ("un-squishes" the plane) to report real-world sizes, angles, and
distances — with an honest confidence readout. This is classic **single-view planar
metrology**: a known length supplies scale, and everything measured is coplanar with
the stick.

## Locked decisions

| Area | Decision |
|------|----------|
| Scope | Flat surfaces only — features coplanar with the stick (wall, door, window, floor area). |
| Detection | Auto-detect the stick via CV; corners marked manually; manual stick fallback always available. |
| Stick | Fixed 4-band red-white-red-white, equal bands. User sets total real length. The 3 internal band joints are used as extra scale samples + a consistency check. |
| Outputs | Surface size + area + diagonal; corner angles / squareness; any two-point distance; annotated image/PDF export with a diagnostics panel. |
| Capture | In-app camera only (CameraX) — true lens intrinsics + distortion + IMU gravity captured at shutter. |
| Engine | Hybrid (C): rectangle solver + gravity solver behind one generic interface, auto-selected with confidence reporting. |
| Accuracy target | ~1–5% with good marking; stated honestly in-app. |

## User flow

1. **Capture** — frame the surface (stick against it), shoot in-app. App records lens
   intrinsics, lens-distortion coefficients, and the gravity vector at shutter time.
2. **Detect stick** — color-segment the red bands, fit the stick centre line, locate
   the 2 ends + 3 band joints. Low confidence → prompt the user to tap the 2 ends.
3. **Mark corners** — user taps 4 corners; a magnifier loupe enables precise placement;
   handles are draggable for fine-tuning.
4. **Solve** — geometry engine recovers plane orientation, fixes scale from the stick,
   computes all measurements.
5. **Read & export** — results + confidence panel; tap any two points for extra
   distances; export annotated image/PDF and share.

## Geometry engine (hybrid)

Both solvers consume the same input — *(undistorted marked points, camera intrinsics,
gravity vector, stick real length)* — and produce the same output — *(rectified plane
solution, measurements, confidence/diagnostics)*. A selector chooses between them.

- **Rectangle solver** — assumes the surface is a true rectangle. Derives the plane tilt
  from how the surface edges converge (vanishing points) combined with the lens
  intrinsics. Most accurate when corners are near-square.
- **Gravity solver** — uses the gravity vector plus a "vertical wall / horizontal floor"
  assumption to recover tilt without assuming a rectangle. Handles non-rectangular
  surfaces; lower accuracy; valid only for cleanly vertical/horizontal planes.
- **Selector** — scores rectangularity of the marked corners and geometric conditioning.
  Near-square + well-conditioned → rectangle solver; otherwise → gravity solver. The
  chosen solver and a confidence score are always surfaced to the user.

## Scale from the stick

Project the stick's 5 points (2 ends + 3 band joints) into the rectified plane. The known
total length and the three equal sub-segments yield several independent scale estimates.
Their **median** sets real scale; their **disagreement** is a quality signal (flags a
non-coplanar stick or a misdetection).

## Measurements derived

Once scale is fixed, every marked point maps to real-world plane coordinates, giving:
surface width, height, diagonal, area (polygon); the 4 corner angles + an out-of-square
readout; and arbitrary two-point distances on the same plane.

## Architecture

Single `:app` module, split by package. Every file kept under ~200 lines.

- **`camera`** — CameraX capture; extracts lens intrinsics + distortion; samples gravity
  at shutter.
- **`vision`** — OpenCV: undistort image; detect stick (red HSV segmentation → centre-line
  fit → ends + band joints); detection confidence.
- **`geometry`** — *pure Kotlin, no Android deps*: vanishing points, rectangle solver,
  gravity solver, selector, scale solver, measurements. Fully JVM-unit-testable; built
  test-first.
- **`model`** — data classes: Point2/Vec, CameraIntrinsics, GravityVector, StickProfile,
  MarkedScene, PlaneSolution, MeasurementResult, Diagnostics.
- **`ui`** — Compose screens (Capture, MarkPoints + magnifier, Results + diagnostics,
  Settings); each composable in its own file.
- **`export`** — annotated bitmap/PDF rendering + share intent.
- **`data`** — DataStore: stick length, units (m / cm / ft-in), preferences.

## Processing pipeline

Undistort → 4 corners → vanishing points → (with intrinsics) plane tilt → rectifying
homography (photo → true-shape view, up to scale) → project stick points → median scale +
agreement check → map all points to real coordinates → width / height / area / diagonal /
corner angles / two-point distances. The gravity solver substitutes
"gravity + vertical/horizontal assumption → tilt" for the vanishing-points step.

## Error handling & confidence

- Stick detection fails → manual tap path (always available).
- Shallow/extreme angle or non-square corners → warn, lower confidence, suggest re-shoot.
- Stick sub-segments disagree → flag possible non-coplanar stick or misdetection.
- Device lacks distortion data → fall back to a sensible default + note reduced accuracy.
- Every result shows the solver used, a confidence figure, an approximate error band,
  camera tilt, viewing angle, and the pixel-to-cm scale.

## Tech / dependencies

Kotlin + Jetpack Compose (scaffolded), **CameraX**, **OpenCV Android SDK 4.x** (accepted
app-size cost for solid CV), Jetpack DataStore, device rotation-vector/gravity sensor,
JUnit for geometry tests.

## Testing

- **`geometry`** — JVM unit tests with *synthetic scenes*: build a known plane + camera,
  project to image, feed points back, assert recovered numbers within tolerance. Covers
  both solvers, the selector, the scale solver, and degenerate cases (shallow angle,
  non-square corners).
- **`vision`** — golden-image tests on sample stick photos.
- **`ui`** — instrumented tests for the mark-and-recompute flow.

## Out of scope (v1)

Cross-plane / room 3D, ARCore depth, fully-automatic corner detection, live viewfinder
HUD, configurable / multi-profile sticks, gallery import.

## Assumptions

- The stick lies in the same plane as the measured features.
- The user can reach a moderate oblique shooting angle (not perfectly head-on, not extreme).
- Most v1 targets are near-rectangular; the gravity solver covers the rest at lower accuracy.
- Target devices expose lens intrinsics via Camera2 (distortion optional, with fallback).
