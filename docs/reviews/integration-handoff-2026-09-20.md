# Metrologist integration handoff — 2026-09-20

The pending app repair, contract graph, deterministic supervisor and calibration
work is ready for integration. The six missing F-Droid settings from the earlier
review are implemented. The old “mid-debug / six gaps missing” description no
longer describes this source tree.

## Delivered work

1. Measurement/capture repairs and regressions: aligned image/gravity frames,
   explicit solver eligibility and failures, valid reference/marker geometry,
   reachable surface selection, stable reference input and recoverable capture.
2. An Obsidian contract graph with build ownership/PID/timestamp history, the
   deterministic `cyp opus high` supervisor, retained review records and reusable
   calibration checks. Runtime evidence remains local in `.supervisor/`.
3. R8/resource shrinking with OpenCV JNI keep rules; unstripped native libraries;
   dependency metadata disabled; literal version **0.0.2 / code 2**; foojay removed;
   English Fastlane metadata; source-versioned release workflow and CI coverage.

The illustrated English/Persian website and checker reference were already
published in commit `b460b79`. A red handle's centre belongs on each outside
corner of the black outline, enclosing the entire checker border.

## Fresh integration checks

Completed at `2026-09-20T07:45:47.646256+00:00` on the isolated `emulator-5556`:

- `buildSmoke :app:connectedDebugAndroidTest :app:assembleRelease --rerun-tasks`
  passed on JDK 17: **384 JVM tests and 19 Android tests**, no failures or skips.
  Every C01–C15 edge assertion and both rendered calibration targets ran.
- Lint: **0 errors, 15 existing warnings**; optimized release APK assembled.
- **40 supervisor tests and 3 release-version tests** passed.
- Graph validation: **10 notes, 15 contracts, 54 links, 11 execution tasks**.
- Independent calibration geometry assertions passed.
- All **24 packaged native libraries** match the original AAR bytes. The five
  inspected OpenCV JNI classes retain their names, with no missing R8 rules.
- All 87 production Kotlin files match the previously reviewed/tested source.
- The previous F-Droid readiness check produced byte-identical JDK 17/JDK 21 APKs
  and passed the APK scanner/signing-block inspection. See the
  [detailed readiness report](fdroid-readiness-2026-09-20.md).

The isolated emulator was stopped after the Android tests. No worker agents were
restarted for integration. CI now makes the graph/supervisor/release-tool checks
part of the required `verify` result alongside the app build.

## Remaining release handoff

The source changes and six F-Droid fixes are ready. A successful measurement on a
physical phone with the optimized build, including Results/diagnostics and saved
settings after restart, is still unconfirmed. The emulator smoke check initialized
OpenCV, captured, displayed failure diagnostics and recovered, but its synthetic
camera metadata prevented a successful measured result. The existing 16 KB
native-library compatibility warning also remains documented.

A fresh public APK and F-Droid submission must not be reported as already verified.
The configured workflow can produce `v0.0.2` from the integrated commit; all four
signing secret names are present. After runtime confirmation and publishing, run
clean-SDK preflight against that exact commit and developer-signed APK, then the
real JDK 21 F-Droid recipe and auto-update checks. No F-Droid submission is part of
this integration PR.

## Evidence locations

`.supervisor/integration-handoff/` retains `full-android-build.log`, copied JUnit
and lint reports in `results/`, `verification.json` (including source hashes),
`native-verification.json`, graph/supervisor/release/calibration check logs and
commit/publication logs. Earlier full run records remain intact under
`.supervisor/reports/`, `.supervisor/graph-records/` and `.supervisor/fdroid/`.
Personal Obsidian tab history and Kotlin compiler runtime files are ignored,
retained locally and excluded from public commits.
