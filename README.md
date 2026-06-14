# Metrologist

[![CI](https://github.com/cocodedk/Metrologist/actions/workflows/ci.yml/badge.svg)](https://github.com/cocodedk/Metrologist/actions)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)

Metrologist measures real-world flat surfaces — walls, doors, windows, floor areas — from a single photo taken with your Android camera. Place a red-white-red-white reference stick of known length against the surface, shoot in-app, tap the four corners, and the app recovers the plane geometry from the perspective distortion to report width, height, area, diagonal, and corner angles in metres, centimetres, or feet and inches. Accuracy is typically 1–5 % with careful marking. The result screen shows which solver was used, a confidence score, and an approximate error band; you can export an annotated PNG to share or archive.

## Website

- English: <https://cocodedk.github.io/Metrologist/>
- Persian / فارسی: <https://cocodedk.github.io/Metrologist/fa/>

## Features

- **Single-photo metrology** — no depth sensor or AR kit required; one well-framed shot is enough.
- **Red-white-red-white stick** — four equal bands; OpenCV detects the two ends and three band joints automatically. Manual fallback is always available.
- **Two plane solvers, auto-selected** — a rectangle solver (vanishing-point geometry) for near-square surfaces, and a gravity solver (IMU vector) for anything else. The app picks the better-conditioned one and tells you.
- **Scale from the stick** — five detected points give up to four independent scale estimates; their median sets real scale and their spread is a quality signal.
- **Zoom and magnifier** — pinch-to-zoom canvas with a loupe magnifier for precise corner placement; handles are draggable after initial tap.
- **Full output set** — width, height, diagonal, area, and interior angles at each corner with an out-of-square readout.
- **Units** — metres, centimetres, or feet and inches; switch any time in settings.
- **PNG export** — annotated photo with the measurement summary overlaid; shared via the standard Android share sheet.
- **Honest confidence** — every result shows solver type, confidence percentage, camera tilt, viewing angle, and pixel-to-real scale.

## Download

Stable release APK (sideload):

```
https://github.com/cocodedk/Metrologist/releases/latest/download/Metrologist.apk
```

## Build from Source

**Prerequisites:** JDK 17, Android SDK with build-tools matching `AGP 9.1.1`, and an internet connection for Gradle to fetch dependencies.

```bash
git clone https://github.com/cocodedk/Metrologist.git
cd Metrologist

# Install the repo Git hooks (pre-commit, commit-msg, pre-push)
./scripts/install-hooks.sh

# Build a debug APK
./gradlew :app:assembleDebug

# Run the ~219 JVM unit tests (no device required)
./gradlew :app:testDebugUnitTest

# Lint
./gradlew :app:lintDebug

# Build + test + lint in one shot
./gradlew buildSmoke

# JaCoCo coverage report
./gradlew :app:jacocoTestReport
```

The output APK lands at `app/build/outputs/apk/debug/app-debug.apk`.

## How it works

1. **Capture** — shoot in-app. CameraX records the frame together with the lens intrinsics from Camera2 and the gravity vector from the device IMU.
2. **Detect** — OpenCV segments the red HSV bands, fits a principal axis through the stick body, and locates two ends and three band joints. If detection confidence is low, the app asks you to tap the endpoints manually.
3. **Mark** — tap the four surface corners on the photo. A loupe magnifier helps with precision; handles can be dragged to adjust.
4. **Solve** — the rectangle solver derives the plane tilt from how opposite surface edges converge to vanishing points, combined with the lens intrinsics. If corners are too skewed or the view angle is too shallow, the gravity solver takes over, using the IMU vector and a vertical-wall / horizontal-floor assumption instead.
5. **Scale** — the five stick points are projected into the recovered plane. Each of the four equal sub-segments gives an independent scale estimate; their median fixes real scale and their spread flags a non-coplanar stick or a misdetection.
6. **Measure** — every marked point maps to real-world plane coordinates, giving width, height, area (shoelace formula), diagonal, and interior corner angles.

## Architecture

Single `:app` module, `com.cocode.measureapp`, split by responsibility. Every file is kept under 200 lines.

```
app/src/main/java/com/cocode/measureapp/
├── geometry/          # Pure Kotlin — no Android imports
│   ├── Vec.kt         # Vec2, Vec3
│   ├── Mat3.kt        # 3×3 matrix with inverse
│   ├── CameraIntrinsics.kt
│   ├── Projective.kt  # Homogeneous line / vanishing point helpers
│   ├── RectangleSolver.kt
│   ├── GravitySolver.kt
│   ├── SolverSelector.kt
│   ├── ScaleSolver.kt
│   ├── Measurements.kt
│   ├── MetrologyEngine.kt
│   ├── Projection.kt
│   ├── Model.kt       # StickProfile, PlaneFrame, PlaneSolution, …
│   ├── Tolerances.kt
│   └── …
├── core/              # Pure Kotlin — presentation helpers, units, corner ordering
├── stick/             # Pure Kotlin — axis fitting, band scoring, StickAssembler
├── model/             # Pure data classes shared across layers
├── capture/           # Android: CameraX, IntrinsicsExtractor, GravityProvider
├── detect/            # Android: OpenCV HSV segmentation → OpenCvStickDetector
├── ui/                # Jetpack Compose screens (Capture, Mark, Results, Settings)
│   └── theme/
├── export/            # AnnotatedExporter — bitmap annotation + share intent
└── data/              # DataStore settings repository
```

**Layer rule:** `geometry`, `core`, and `stick` must not import any Android class. They are tested as plain JVM unit tests with no emulator.

| Technology | Version / notes |
|---|---|
| Kotlin | 2.2.10 |
| Jetpack Compose | Material 3 |
| AGP | 9.1.1 |
| CameraX | in-app capture, Camera2 intrinsics |
| OpenCV Android SDK | 4.x, HSV segmentation |
| Jetpack DataStore | settings persistence |
| JUnit 4 | ~219 unit tests |
| JaCoCo | 100 % line/branch/method on geometry/core/stick |
| minSdk / targetSdk | 24 / 36 |

## Author

**Babak Bandpey** — [cocode.dk](https://cocode.dk) | [LinkedIn](https://linkedin.com/in/babakbandpey) | [GitHub](https://github.com/cocodedk)

## License

Apache-2.0 | © 2026 [Cocode](https://cocode.dk) | Created by [Babak Bandpey](https://linkedin.com/in/babakbandpey)
