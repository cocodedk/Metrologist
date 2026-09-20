---
id: N02
type: work
spec_status: ready
build_status: built
build_agent: "frames02/2"
build_pid: 313900
build_pid_note: recorded
build_attempt: 2
build_started_at: "2026-09-19T14:33:17Z"
build_ended_at: "2026-09-19T19:15:26Z"
build_verified_at: "2026-09-19T19:15:26Z"
build_requested_action: none
build_request_id: 0
build_acknowledged_request_id: 0
build_heartbeat_at: "2026-09-19T14:44:05Z"
build_files: []
build_last_error: null
build_evidence: ["docs/reviews/graph-loop-20260919T191526Z.md", ".supervisor/reports/integration-6/manifest.json", ".supervisor/frames02-2-check.log", ".supervisor/graph-records/20260919T191526Z/state.json"]
priority: high
finding: F2
tags: [logic-contracts]
---

# Capture coordinate frame

## Problem and evidence

Capture currently converts `ImageProxy` to a bitmap, closes the proxy, and
attaches gravity without preserving the image's rotation transform. The gravity
provider uses device-derived axes, which need not match the camera buffer axes.
A synthetic 90-degree frame mismatch changed a 2 by 1 m result to approximately
2.07 by 0.79 m. This is synthetic evidence, not a tested handset result.

## Required changes

- Establish the upright marking bitmap as the canonical pixel frame. Transform
  the image, intrinsics, and camera-frame gravity consistently into that frame.
- Read rotation, crop information, image dimensions, and the bound camera's
  mounting orientation before closing the capture. Preserve enough metadata
  to explain and test the device-to-buffer-to-marking transforms.
- Account for crop offsets, scaling, axis swaps, and principal-point movement.
  Rotating only the bitmap or only gravity is not sufficient. Intrinsics must
  describe the actual marked pixels, with finite positive focal lengths.
- Keep raw sensor, camera buffer, marking, and display frames distinct. Apply
  each transform exactly once. Display rotation for the level overlay must not
  be reapplied to an already aligned measurement vector.
- Associate the gravity sample with the shot. Use the newest sample at or before
  exposure, with age at most 100 ms, as an explicit initial policy. Compare
  nanoseconds only when both timestamps are confirmed in `elapsedRealtimeNanos`.
  For camera timestamps this requires a verified REALTIME source; UNKNOWN,
  missing, future or stale samples disable gravity. Do not substitute callback
  receipt time or wall-clock time for exposure time. See the [camera timestamp
  source contract](https://developer.android.com/reference/android/hardware/camera2/CameraCharacteristics#SENSOR_INFO_TIMESTAMP_SOURCE).
- Represent unavailable calibration/transform metadata explicitly. Do not
  present an unverified approximation as calibrated geometry.

## Code and test touchpoints

- [CameraHelpers.kt](../../app/src/main/java/com/cocode/measureapp/ui/CameraHelpers.kt)
- [IntrinsicsExtractor.kt](../../app/src/main/java/com/cocode/measureapp/capture/IntrinsicsExtractor.kt)
- [CapturedScene.kt](../../app/src/main/java/com/cocode/measureapp/model/CapturedScene.kt)
- [CameraIntrinsics.kt](../../app/src/main/java/com/cocode/measureapp/geometry/CameraIntrinsics.kt)
- [SyntheticScene.kt](../../app/src/test/java/com/cocode/measureapp/geometry/SyntheticScene.kt)

Build pure transform tests plus an Android capture integration test. CameraX's
[capture callback contract](https://developer.android.com/reference/androidx/camera/core/ImageCapture.OnImageCapturedCallback)
is the API reference for buffer rotation metadata.

## Incoming contracts

- [[08-capture-recovery#C01 Complete capture]] supplies the image and capture metadata together.
- [[07-gravity-direction-level#C02 Downward vector]] supplies gravity with declared device axes and validity.

## Outgoing contracts

### C03 Aligned projection

**Consumer:** [[01-rectangle-vanishing-points]].

Object/stick pixels and intrinsics must describe the canonical marking bitmap.
Preserve image identity across marking and measurement. A missing or invalid
transform must prevent a calibrated rectangle candidate, not produce a guessed
high-confidence one. Keep calibration quality/source attached to that scene:
`calibrated`, `approximate`, or `unavailable`, with the origin of the intrinsics.

**Edge assertion:** rotate the same synthetic capture through 0, 90, 180, and
270 degrees and apply a known crop/resize. After alignment, corresponding rays
match within `1e-9`, and recovered dimensions match within relative `1e-6`.

### C04 Aligned gravity

**Consumer:** [[03-solver-eligibility-confidence]].

Supply the aligned scene identity, intrinsics and calibration quality/source
(`calibrated`, `approximate`, or `unavailable`), plus physical-down gravity in
the same camera frame used to back-project marked pixels or an unavailable
reason. Field-of-view and guessed-sensor-size fallbacks are `approximate`;
missing provenance must not be upgraded to `calibrated`. The consumer must disable
gravity candidates when the sample or transform is invalid, while remaining
free to use an independently eligible rectangle solution.

**Edge assertion:** force the gravity branch on a supported synthetic wall/floor
viewed by a tilted camera in all four image orientations. Equivalent scenes
must have equivalent metric
results; a missing/stale sample must not become a default upright vector.
Exercise calibrated and fallback intrinsics through the real selector and
diagnostics; the original quality/source must survive both solver branches.

## Acceptance checks

- Use unequal `fx` and `fy` and an off-centre principal point so symmetry cannot
  hide an incorrect 90-degree transform. Include cropped and uncropped cases.
- Check a rolled/tilted camera, rather than testing only gravity parallel to an
  image axis. Verify both normal direction and resulting measurements.
- In Android tests check a portrait capture, a landscape capture, and display
  rotation between captures; validate that marking sees the expected orientation.
- Verify missing metadata and stale-gravity paths explicitly. Record physical
  device checks separately from mathematical and instrumented tests.
- Test comparable sample ages 99, 100 and 101 ms, a sample after exposure, and
  identical numeric timestamps carrying different clock domains. Accept only
  comparable non-future ages at most 100 ms. REALTIME and UNKNOWN camera sources
  must take distinct paths; an UNKNOWN source cannot pass by numeric coincidence.

## Build history


### Verified run 2026-09-19T19:15:26Z

PIDs are historical; all workers have stopped and file reservations are released. Verification-only attempts reuse the completed worker identity. [Run report](../reviews/graph-loop-20260919T191526Z.md) records the evidence and limits.

| Task / attempt | Worker PID | Start UTC | End UTC | Outcome |
| --- | --- | --- | --- | --- |
| frames02/1 | 272840 | 2026-09-19T14:13:28Z | 2026-09-19T14:29:49Z | failed |
| frames02/2 | 313900 | 2026-09-19T14:33:17Z | 2026-09-19T14:44:37Z | passed |
