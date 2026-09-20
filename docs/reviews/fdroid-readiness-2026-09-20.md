# F-Droid upstream readiness — 2026-09-20

Astra's app-specific checklist was checked against the source. The missing upstream
settings are now implemented locally. This is **not** an F-Droid submission or a
claim that a published release has passed reproducible-build preflight.

## Changes

- Enabled R8 and resource shrinking in release builds. Kept OpenCV classes and
  members because its JNI layer resolves names and 4.11.0 supplies no consumer rules.
- Preserved prebuilt native libraries with `keepDebugSymbols`, avoiding NDK stripping.
- Disabled APK and bundle dependency metadata.
- Added literal `VERSION_NAME=0.0.2` / `VERSION_CODE=2` in `gradle.properties`.
  Gradle and the release workflow read those source values. No release was created.
- Removed the foojay resolver. No JDK toolchain pin was added.
- Release workflow rejects an existing version tag, uses the built commit SHA for
  the release tag, keeps `Metrologist.apk`, and serializes release runs. It does not
  push version commits to the default branch. Updated signing-script instructions.
- Added English Fastlane title, summary, full description and changelog `2.txt`.
  No staged emulator image is presented as a real measurement screenshot.
- CI now considers version/release-script changes and builds the optimized APK.
  Retained release version tests and the native-library/mapping verifier in
  `scripts/release/`.

## Verification

| Check | Result |
| --- | --- |
| JDK 17 `buildSmoke :app:assembleRelease` | Passed; 384 JVM tests; lint 0 errors, 15 existing warnings |
| JDK 21 `:app:assembleRelease --rerun-tasks` | Passed |
| Unsigned APK comparison across JDKs | Byte-for-byte identical |
| Native-library comparison with cached source AARs | All 24 identical, across all packaged ABIs |
| R8 mapping | Mat, Core, Imgproc, OpenCVLoader and Utils retain their names; no missing_rules.txt |
| F-Droid APK scanner | Passed on a locally test-signed copy of the minified APK |
| Signing-block inspection | v2/v3 signatures and verity padding only; no dependency metadata |
| Source-version tests | 3 tests passed, including duplicate tags and malformed/mismatched versions |
| Workflow YAML and metadata | Parsed; metadata size limits and source version wiring checked |
| Updated checker-target Android tests | Both calibration flow tests passed on isolated emulator-5556 |

JDK 17 / JDK 21 unsigned APK SHA-256:

`258f651925123d091ef47bf60bb345f039f40e0831b0fa4b5779d597db87ee8a`

Artifacts reflect the uncommitted app/build changes on the source used for this
check. A final committed release must be rebuilt and verified again against its
own published artifact; this hash is not a future release guarantee.

## Minified runtime check and remaining gates

Installed a copy of the optimized APK signed with a local test key on an isolated
emulator. OpenCV initialized successfully. Camera preview, capture, manual corner
movement, measurement rejection with a diagnostic, retake and Settings navigation
ran without an app crash. The crash buffer was empty. The emulator was stopped.

A successful real measurement and its Results/diagnostics screen remain
**unverified on a physical phone**. The virtual camera reports a non-realtime
clock and approximate calibration; the captured checkerboard was rejected by the
geometry checks. No application validation was weakened to force a result.

The API 37 emulator also displayed the existing 16 KB native-library compatibility
warning and used compatibility mode. This readiness change does not fix that
upstream native-library limitation.

The following gates still precede submission:

1. Exercise the final minified build on a physical phone: a known target,
   successful measurement, Results/diagnostics, restart and saved settings.
2. Integrate the reviewed app changes, publish a developer-signed release, then
   run clean-SDK preflight against that exact commit and published APK.
3. Create and verify the actual F-Droid recipe with the full commit hash, allowed
   signer and auto-update configuration; run `fdroid build --test` on JDK 21 and
   `checkupdates` against the previous version.
4. Obtain the required publishing/submission approval before outward actions.

No release workflow, F-Droid MR or external message was sent. No F-Droid install
link was added because the app is not yet confirmed published there.

## Retained evidence

All commands' build, scan and runtime evidence stays under `.supervisor/fdroid/`:
`build-jdk17.log`, `build-jdk21.log`, `apk-comparison.txt`, per-JDK
`verification.json` and mappings, `scanner.log`, `signing-blocks.txt`,
`version-tests.log`, `static-check.log`, `source-hashes.json`, runtime screenshots,
UI dumps and logcat, and `calibration-android-rerun.log`. Initial failed test setup
attempts are retained too; the final run selected only emulator-5556 and passed.

The supervisor's device journal records emulator ownership, PID, start/end times
and logs. No other emulator or physical phone was selected for installation.

## References

- [Android release optimization](https://developer.android.com/topic/performance/app-optimization/enable-app-optimization)
- [Native-library packaging DSL](https://developer.android.com/reference/tools/gradle-api/9.0/com/android/build/api/dsl/JniLibsPackaging)
- [F-Droid metadata format](https://f-droid.org/en/docs/All_About_Descriptions_Graphics_and_Screenshots/)
