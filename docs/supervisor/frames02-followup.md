# N02 follow-up review

This is feedback for the same N02 assignment after its first attempt, not another
graph node. Keep the original one-predecessor/one-successor context limit.

## Finish the production handoff

Capture recovery extracted the real scene assembly into
`app/src/main/java/com/cocode/measureapp/ui/capture/CaptureRequest.kt`.
The amended assignment includes that file. Wire the existing alignment helper
into this callback, preserving the capture controller's request identity,
metadata-before-close rule, conversion failure handling and bitmap cleanup.
The successful handoff must contain the canonical bitmap and its matching aligned
scene; merely defining `alignCapturedShot` is insufficient. Remove this callback's
use of the legacy `gravity.current()` fallback. Consumer migration to typed
outcomes remains assigned to N03/N05, so report that boundary honestly.

The final `integration` task owns the Android callback/marking assertions for
all fifteen edges, including portrait/landscape capture and display rotation.
Those checks are deferred to that task, not waived. This N02 milestone covers
the production alignment producer and its JVM regressions; do not mark the whole
graph node built. Put remaining physical-device and consumer limitations in the
report rather than treating their assigned later work as a missing write scope.

## Do not overstate calibration

The first implementation's `BufferIntrinsics.fromCalibration` reads the first
four calibration entries, assumes a centred aspect crop from the active array,
ignores skew/distortion, and labels the result calibrated. `IntrinsicsExtractor`
does not establish the pre-correction-to-processed-image mapping for that shot.

Android defines the lens calibration in pre-correction array coordinates.
Mapping it to processed output also involves distortion and the active-array
coordinate system. The two array sizes can differ. See the official
[lens calibration reference](https://developer.android.com/reference/android/hardware/camera2/CameraCharacteristics#LENS_INTRINSIC_CALIBRATION)
and [capture result reference](https://developer.android.com/reference/android/hardware/camera2/CaptureResult#LENS_INTRINSIC_CALIBRATION).

Meet the existing provenance contract: if the actual image mapping is assumed or
unsupported, retain explicit approximate/unavailable provenance and its reason.
Presence of a device calibration array alone must not upgrade an assumed mapping
to calibrated. Do not add a guessed transform to obtain a stronger label. Handle
unsupported nonzero/invalid skew honestly. Explicitly trusted synthetic fixtures
may still supply calibrated intrinsics with a fully specified transform.

Add meaningful regression coverage for device calibration with an unverified
processed-image mapping and for unsupported calibration values. Assert that the
quality/source survives scene alignment. Keep the existing rotation/crop/ray,
timestamp and stale/missing-gravity assertions. The supervisor owns test execution.
Include the missing supported horizontal floor/table case in the pure gravity
alignment regressions as well as the existing wall case. Supporting tests/helpers
may be extracted into the newly reserved test `geometry/frames/` directory to
respect the 200-line limit.
