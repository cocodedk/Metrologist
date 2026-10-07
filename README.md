# Metrologist

[![CI](https://github.com/cocodedk/Metrologist/actions/workflows/ci.yml/badge.svg)](https://github.com/cocodedk/Metrologist/actions)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)

Metrologist estimates the width, height, area, diagonal and corner angles of a flat surface, such as a wall, door, window, floor or table, from one photo taken in the app. Lay a reference stick of known length and width on the same surface, enter its length and width in Settings, take the photo, then drag one box onto the four corners of the object and another onto the four outside corners of the stick. Results appear in metres, centimetres, or feet and inches, with a confidence score and, when relevant, notes. They are estimates, so check important sizes with a ruler or tape. You can share an annotated picture of a result with Export.

## Download

<!-- cocode-apps:install:start -->
- Coming to F-Droid
- [Download the Android installation file (APK) from GitHub](https://github.com/cocodedk/Metrologist/releases/latest/download/Metrologist.apk)
- [Add the app to Obtainium, an app that keeps it up to date](https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/cocodedk/Metrologist)
<!-- cocode-apps:install:end -->

## Website

- English: <https://measure.cocode.dk/>
- Dansk: <https://measure.cocode.dk/da/>
- Persian / فارسی: <https://measure.cocode.dk/fa/>

## Features

- **One photo.** No depth sensor or AR kit is needed; one well-framed shot is enough.
- **Reference stick.** You enter its full length and width in Settings. The stick you can print from the website is 20 × 4 cm, with a red-white-red-white-red centre and a checker border. OpenCV looks for the stick and proposes the red box. You check it and drag its four corners onto the stick's four outside corners.
- **Two measuring methods, chosen for you.** The rectangle method works from the corners you marked (vanishing-point geometry). The tilt-sensor method works from the phone's gravity reading and an assumption that the surface is a vertical wall or a horizontal floor or table. The app uses only a method whose checks pass. If both pass, a method that does not have to assume a wall faces the camera comes first. Between those, the higher confidence wins, and a tie goes to the rectangle method. If neither works, it says why and what to check.
- **Scale from the stick.** The length and the width you entered are matched to the long and the short sides of the red box, and the two scales are combined as a length-weighted average. A method that finds them more than 10 percent apart is rejected.
- **Zoom and magnifier.** Zoom with two fingers; a magnifier appears while you drag a corner.
- **Results.** Width, height, diagonal, area and the four interior corner angles.
- **Units.** Metres, centimetres, or feet and inches; change them any time in Settings.
- **Export.** An annotated picture of the photo and the measurements, shared through the Android share sheet.
- **Confidence and notes.** Every result shows a confidence label (High, Medium or Low) with a percentage, the corner angles, and notes about the photo when there is something to point out. The result screen names the tilt-sensor method when it was used. The score is not a guarantee that the size is right.

## How it works

1. **Capture.** You take the photo in the app. CameraX records the frame together with the camera's lens details (when the phone reports them) and the phone's tilt from its gravity sensor.
2. **Detect.** OpenCV segments the red stripes (HSV), fits a principal axis through the stick and proposes the red box. The detector looks for the older four-stripe pattern, so for the printable stick with its checker border, check all four corners yourself.
3. **Mark.** Drag the corners of the green box onto the four corners of the object, and the corners of the red box onto the four outside corners of the stick. Use two fingers to zoom.
4. **Solve.** The rectangle method derives the plane's tilt from where opposite edges meet (vanishing points) and the lens details. The tilt-sensor method uses the gravity reading and the wall-or-floor assumption. Each method must pass its own checks.
5. **Scale.** The red box's long and short sides are matched to the length and width you entered (see Features).
6. **Measure.** Every marked point is mapped to real-world coordinates on the plane, which gives width, height, area (shoelace formula), diagonal and the four interior corner angles.

## Privacy

Metrologist does not collect personal data, and it does not upload your photos or measurements by itself.
Calculations run on your device, photos are processed in memory, and the app has no internet permission,
analytics, crash reporting or ads. The only permission it asks for is the camera. If you tap Export,
Android's share sheet opens and you choose where the picture goes; the privacy policy of the app you
choose then applies. The buttons on the "About Metrologist" screen open web pages in your browser, only when you tap
them.

Read the full [privacy policy](https://measure.cocode.dk/privacy/).

## Build

**Prerequisites:** JDK 17, Android SDK Platform 36.1, SDK Build Tools compatible with AGP 9.1.1, and an internet connection for Gradle to download dependencies.

```bash
git clone https://github.com/cocodedk/Metrologist.git
cd Metrologist

# Install the repo Git hooks (pre-commit, commit-msg, pre-push)
./scripts/install-hooks.sh

# Build a debug APK
./gradlew :app:assembleDebug

# Run the JVM unit tests (no device required)
./gradlew :app:testDebugUnitTest

# Lint
./gradlew :app:lintDebug

# Build + test + lint in one shot
./gradlew buildSmoke

# JaCoCo coverage report
./gradlew :app:jacocoTestReport
```

The output APK lands at `app/build/outputs/apk/debug/app-debug.apk`.

## Architecture

Single `:app` module, `com.cocode.measureapp`, split by responsibility. The guideline is to keep files under 200 lines; one test file, `StickAssemblerTest.kt`, is longer.

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
├── ui/                # Jetpack Compose screens (Camera, Mark, Results, Settings, Help, About)
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
| JUnit 4 | JVM unit tests |
| JaCoCo | line, branch and method coverage report for the JVM tests (`:app:jacocoTestReport`) |
| minSdk / targetSdk | 24 / 36 |

## Contributing

Local setup, the Git hooks, the build and test commands, coding style, branch naming and the pull request
checklist are in [CONTRIBUTING.md](CONTRIBUTING.md). Bugs and ideas go to the
[issues page](https://github.com/cocodedk/Metrologist/issues).

## Author

**Babak Bandpey** — [cocode.dk](https://cocode.dk) | [LinkedIn](https://linkedin.com/in/babakbandpey) | [GitHub](https://github.com/cocodedk)

## License

Apache-2.0 | © 2026 [Cocode](https://cocode.dk) | Created by [Babak Bandpey](https://linkedin.com/in/babakbandpey)
