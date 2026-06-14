# Metrologist — Claude Code project context

## Project Overview

**Metrologist** is an open-source Android app that measures real flat surfaces (walls, doors, windows, floor areas) from a single photo. The user places a known-length red-white-red-white reference stick against the surface, shoots in-app, marks four corners and optionally the stick ends, and the app returns width, height, area, diagonal, corner angles, and a confidence score.

**Repo:** github.com/cocodedk/Metrologist  
**Package:** `com.cocode.measureapp`  
**Stack:** Kotlin 2.2.10, Jetpack Compose (Material 3), AGP 9.1.1, CameraX, OpenCV 4.x, Jetpack DataStore  
**minSdk / targetSdk:** 24 / 36  
**Tests:** ~219 JVM unit tests; 100 % line/branch/method coverage on the pure-Kotlin packages  
**License:** Apache-2.0, Copyright 2026 [Cocode](https://cocode.dk)

### Architecture

Two clear layers:

**Pure-Kotlin metrology engine** — no Android imports; fully testable as plain JVM unit tests:

- `geometry/` — math primitives (`Vec2`, `Vec3`, `Mat3`, `CameraIntrinsics`, `Projective`), both plane solvers (`RectangleSolver`, `GravitySolver`), auto-selector (`SolverSelector`), scale solver (`ScaleSolver`), measurements (`Measurements`), and the top-level engine (`MetrologyEngine` with `measure` / `measureHybrid`).
- `core/` — presentation helpers (`MeasurementPresenter`, `DiagnosticsText`), unit formatting (`Units`, `LengthUnit`), and corner ordering (`CornerOrdering`).
- `stick/` — stick geometry: principal-axis fitting (`PrincipalAxis`), band-pattern scoring, and assembly (`StickAssembler`, `StickBox`).
- `model/` — shared pure data classes (`CapturedScene`, etc.).

**Thin Android shell** — the only code that touches Android APIs:

- `capture/` — CameraX capture, Camera2 intrinsics extraction (`IntrinsicsExtractor`), IMU gravity sampling (`GravityProvider`).
- `detect/` — OpenCV HSV segmentation and stick detection (`OpenCvStickDetector`).
- `ui/` — Jetpack Compose screens: `CameraScreen`, `MarkScreen` (pinch-zoom canvas + magnifier loupe + draggable handles), `ResultsScreen`, `SettingsScreen`.
- `export/` — annotated bitmap export and Android share intent (`AnnotatedExporter`).
- `data/` — DataStore settings persistence (`SettingsRepository`).

**Layer rule:** `geometry`, `core`, `stick`, and `model` must never import any `android.*` or `androidx.*` class. Violating this breaks JVM-only unit testing.

## Required Skills

Before starting any task of the matching kind, load and follow the listed skill:

| Task kind | Skill to invoke first |
|---|---|
| Adding or changing any feature — UI or engine | `superpowers:brainstorming` |
| Multi-step work with more than ~3 distinct files | `superpowers:writing-plans` |
| Any new code in `geometry`, `core`, or `stick` | `superpowers:test-driven-development` |
| Diagnosing a failing test or unexpected behavior | `superpowers:systematic-debugging` |
| Before marking a branch ready for review | `superpowers:requesting-code-review` |
| Before claiming any task complete | `superpowers:verification-before-completion` |
| Any change to Compose screens or visual layout | `frontend-design:frontend-design` |
| After implementing a feature | `simplify` |

## Coding Conventions

- **File size** — hard limit of 200 lines per file. Split helpers, data classes, and composables into separate files.
- **Immutable models** — `data class` with `val`; no mutable fields in pure packages.
- **Pure functions** — prefer functions that return values over functions that mutate state. Side effects belong in `capture`, `detect`, `ui`, `export`, and `data` only.
- **Strict typing** — no `Any`, no unchecked casts, no unnecessary nullability. Use `require` / `check` to guard invariants at boundaries.
- **Tolerances** — use the named constants in `Tolerances.kt` (`NORM_EPS`, `PROJ_EPS`); never scatter raw numeric literals.
- **Corner ordering** — corners are always `[TL, TR, BR, BL]` clockwise. Document and `require` this at every public entry point.
- **Commit messages** — Conventional Commits: `type(scope): description`. Types in use: `feat`, `fix`, `refactor`, `test`, `docs`, `chore`, `ci`.

## Engineering Principles

- **200-line file limit** — non-negotiable. If a file is growing, extract.
- **DRY / SOLID / KISS / YAGNI** — one reason to exist per class; no speculative abstraction.
- **TDD for engine code** — write the failing test first, then the implementation, for every new function in `geometry`, `core`, or `stick`. The synthetic-scene oracle in `SyntheticScene.kt` is the ground-truth harness for end-to-end solver tests.
- **Conventional Commits** — enforced by the commit-msg hook; `./scripts/install-hooks.sh` must be run after every clone.

## Build Commands

```bash
# Debug APK
./gradlew :app:assembleDebug

# Unit tests (no device required)
./gradlew :app:testDebugUnitTest

# Lint
./gradlew :app:lintDebug

# Build + test + lint (CI gate / smoke)
./gradlew buildSmoke

# Coverage report
./gradlew :app:jacocoTestReport
```

## Key Files

| File | Purpose |
|---|---|
| `app/src/main/java/…/geometry/MetrologyEngine.kt` | Top-level engine: `measure` (rectangle only) and `measureHybrid` (auto-select) |
| `app/src/main/java/…/geometry/RectangleSolver.kt` | Vanishing-point plane solver |
| `app/src/main/java/…/geometry/GravitySolver.kt` | IMU gravity plane solver |
| `app/src/main/java/…/geometry/SolverSelector.kt` | Picks the better-conditioned solver |
| `app/src/main/java/…/geometry/ScaleSolver.kt` | Median scale from stick segments |
| `app/src/main/java/…/geometry/Measurements.kt` | Width / height / area / diagonal / corner angles |
| `app/src/main/java/…/stick/StickAssembler.kt` | Assembles stick points from CV output |
| `app/src/main/java/…/detect/OpenCvStickDetector.kt` | OpenCV HSV detection → StickAssembler |
| `app/src/main/java/…/ui/MarkScreen.kt` | Marking canvas with zoom and magnifier |
| `app/src/test/java/…/geometry/SyntheticScene.kt` | Ground-truth oracle for solver tests |
| `docs/superpowers/specs/2026-06-13-measure-app-design.md` | Full design spec |
| `docs/superpowers/plans/` | Implementation plans (geometry, engine, gravity, UI, detection) |
| `scripts/install-hooks.sh` | Installs `.githooks` into local Git config |
| `build.gradle.kts` | Root build file; defines the `buildSmoke` task |

## Starting a New Session

1. Read `docs/superpowers/specs/2026-06-13-measure-app-design.md` to ground yourself in the design intent.
2. Run `./gradlew buildSmoke` to confirm the tree is green before touching anything.
3. Check `docs/superpowers/plans/` for any in-progress plan that applies to your task.
4. Load the required skill for your task type (see the table above) before writing code.
