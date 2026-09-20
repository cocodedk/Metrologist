# Calibration target and app verification — 2026-09-19

## Page

Rebuilt `tools/calibration-target.html` with physical screen-scale confirmation,
fixed-size SVG geometry, explicit resizing, photo mode, rectangle/nonrectangle
cases, print/SVG export, and app-result comparison with downloadable records.
Default dimensions: 23 x 15 cm; reference: 10 x 2 cm; rectangle area: 345 cm².
Geometry, display, interaction and retained verification helpers live in
`tools/calibration/`; its README documents usage and reproducible checks.

Verified:

- Independent rectangle/nonrectangle geometry, rotation invariance, rejected
  invalid dimensions and numeric error comparison (`node tools/calibration/check.cjs`).
- Browser calibration controls, unchanged target size in photo mode,
  nonrectangle angles, comparison success/failure, stale-record invalidation,
  display-scale invalidation, reload confirmation and mobile fitting.
- Desktop and mobile screenshots were visually inspected.

Browser scale confirmation was simulated; the physical monitor was not measured.
Exact browser output is in `.supervisor/calibration-target/browser-check.log`.
Screenshots are in `output/playwright/`.

## Actual app smoke check

The updated APK was installed on the dedicated `emulator-5556`, leaving the
physical phone and unrelated emulator untouched. The app launched, captured
an image, rejected inconsistent default marks with an explanation, returned to
the camera through Retake, and captured another image without an app restart.
UI hierarchy evidence and the crash buffer are retained in
`.supervisor/calibration-target/`. This check did not measure the page target.

Observed limits:

- The Android emulator displayed a 16 KB native-library compatibility warning
  and ran the app in compatibility mode. The original warning screenshot is
  `output/playwright/android-camera-smoke.png`.
- The virtual camera reports a non-REALTIME exposure clock. The app reported
  `CAMERA_CLOCK_NOT_REALTIME` and correctly disabled its gravity candidate.
- Loading the page image with `virtualscene-image wall` returned OK, but the
  current camera pose did not show that poster; no numeric target result is
  claimed from this camera run.

## Calibration images through the Android marking flow

The page's actual SVG artwork is rendered into retained PNGs and JSON fixtures
at `app/src/androidTest/assets/calibration-target/`. Independent JSON ground
truth includes edge lengths, polygon area, diagonals and corner angles. Camera
intrinsics and physical-down direction are explicitly synthetic.

The compiled production engine was independently exercised against both page
fixtures with `python3 tools/calibration/check-engine.py` at 18:31:28 UTC.
Both passed a relative tolerance of 1e-6 for dimensions, area, diagonals and
all corner angles:

| Target | App width | App height | App area | Selected method |
| --- | --- | --- | --- | --- |
| Rectangle | 23.00 cm | 15.00 cm | 345.00 cm² | Rectangle, confidence 0.9152 |
| Nonrectangle | 21.10353 cm | 14.58246 cm | 303.18182 cm² | Gravity, confidence capped at 0.35 |

Nonrectangle angles were 90.0000°, 105.7013°, 77.6917°, 86.6070°; they were
not forced to right angles. Exact commands, fixture/engine bytecode SHA-256
hashes and numeric results are retained in
`.supervisor/calibration-target/engine-probe/`. This is an engine verification,
not a physical camera or Android UI test.

The Graph-Loop integration worker completed the Android marking/presenter/results
tests and the C01–C15 edge assertions. **The final combined gate passed at
19:15:26 UTC: 19 Android tests, 384 JVM tests, debug build and lint (no errors,
15 warnings; the existing baseline is retained).** Both calibration targets
passed the marking, engine, presenter, Results and stale-result checks.
See the [completed run](graph-loop-20260919T191526Z.md).
Failed attempts are preserved in `.supervisor/reports/integration-2/` and
`.supervisor/reports/integration-3/` (and later numbered attempts):

- Espresso 3.5.1 could not access `InputManager.getInstance` on the API 37
  emulator. The test dependency was updated to 3.7.0, whose official
  [release notes](https://developer.android.com/jetpack/androidx/releases/test)
  describe replacing that reflection with `getSystemService`.
- The first actual fixture assertions found a 762 x 523 PNG with metadata
  declaring 762 x 522. The exporter now aligns the SVG to integer pixels and
  verifies the PNG header dimensions before writing metadata. Both regenerated
  fixtures are exactly 762 x 522.
- The instrumentation process disappeared during guest memory pressure; no Java
  crash was recorded. The owned emulator was restarted with 4096 MiB of guest
  RAM. This was not host swap exhaustion.

Attempt 4 completed all 18 Android tests without a process crash. Both
calibration-page tests passed. One unrelated contract assertion exposed a real
settings input problem: a precise decimal-feet edit could be replaced with its
four-decimal display representation, changing 0.1 m to 0.10000488 m.
`LengthField` now preserves the editor text when the stored value echoes back.
The coordinator also added direct `SettingsRepository`/DataStore coverage for
invalid setter calls preserving both dimensions and the unrelated display unit.
Attempt 5 passed all 19 Android tests and all 384 JVM tests, including the
precision regression and direct repository test. Its lint check rejected a
Kotlin opt-in annotation used with the Java Camera2 marker. The annotation was
corrected to `androidx.annotation.OptIn`; attempt 6 passed the full gate.
All earlier reports remain preserved, along with 139 SHA-256-verified report
files from the successful attempt in `.supervisor/reports/integration-6/`.

These tests use real screens and production measurement logic inside a test
host. CameraX, DataStore and the top-level `MeasureApp` wiring are not covered
by those fixture tests; the separate settings test covers DataStore directly.
Capture metadata and sensor samples are simulated.

A real phone photo of a physically calibrated display remains a separate check.
