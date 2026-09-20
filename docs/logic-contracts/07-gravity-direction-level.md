---
id: N07
type: work
spec_status: ready
build_status: built
build_agent: "gravity07/1"
build_pid: 226656
build_pid_note: recorded
build_attempt: 1
build_started_at: "2026-09-19T13:45:10Z"
build_ended_at: "2026-09-19T19:15:26Z"
build_verified_at: "2026-09-19T19:15:26Z"
build_requested_action: none
build_request_id: 0
build_acknowledged_request_id: 0
build_heartbeat_at: "2026-09-19T13:55:18Z"
build_files: []
build_last_error: null
build_evidence: ["docs/reviews/graph-loop-20260919T191526Z.md", ".supervisor/reports/integration-6/manifest.json", ".supervisor/gravity07-1-check.log", ".supervisor/graph-records/20260919T191526Z/state.json"]
priority: medium
finding: F7
tags: [logic-contracts]
---

# Gravity direction and level

## Problem and evidence

The provider flips sensor y/z coordinates but does not reconcile Android's
stationary acceleration convention with the engine's physical-down convention.
For an upright phone, its current output becomes `(0,-1,0)` in the claimed
camera-like frame. `tiltFromGravity` then reports 180 degrees pitch and not level.

The sign mismatch was checked against the [Android sensor values contract](https://developer.android.com/reference/android/hardware/SensorEvent#values)
and a pure tilt calculation; the review did not test a physical handset.

## Required changes

- Separate physical direction from coordinate conversion. Decode a sensor
  sample into a physical-down vector in declared device axes first; do not call
  that vector camera-frame gravity before the camera transform is applied.
- Replace the ambiguous camera-frame approximation with explicit frame and
  validity semantics. Node 02 owns the device-to-image transform.
- For the level overlay, separately transform physical down into the current
  display/view axes expected by `tiltFromGravity`. Preserve rotation support
  and avoid applying the image-frame transform a second time.
- Report an upright level pose as pitch/roll zero. Preserve the engine's
  diagnostic pitch convention: looking up is positive, looking down negative.
  The overlay's existing pitch convention may differ, but must be documented
  and tested rather than silently conflated with diagnostic pitch.
- Treat absent sensors, no sample yet, and invalid vectors as unavailable.
  The initial synthetic upright vector must not masquerade as a sensor reading.
- Re-derive the landscape transforms and `TiltTest` expectations alongside the
  provider sign fix; old 90/270-degree fixtures are not independent ground truth.
  Give the overlay an explicit unavailable state rather than an upright
  `TiltAngles` fallback for zero/missing input. The UI must not show unavailable
  sensor data as a green/level indicator.

## Code and test touchpoints

- [GravityProvider.kt](../../app/src/main/java/com/cocode/measureapp/capture/GravityProvider.kt)
- [Tilt.kt](../../app/src/main/java/com/cocode/measureapp/geometry/Tilt.kt)
- [CameraScreen.kt](../../app/src/main/java/com/cocode/measureapp/ui/CameraScreen.kt)
- [LevelOverlay.kt](../../app/src/main/java/com/cocode/measureapp/ui/LevelOverlay.kt)
- [TiltTest.kt](../../app/src/test/java/com/cocode/measureapp/geometry/TiltTest.kt)
- [CameraTiltSignTest.kt](../../app/src/test/java/com/cocode/measureapp/geometry/CameraTiltSignTest.kt)

## Incoming contracts

None. This node is the boundary between Android sensor semantics and physical
gravity. It does not derive plane orientation or calibration confidence.

## Outgoing contracts

### C02 Downward vector

**Consumer:** [[02-capture-coordinate-frame]].

Provide a finite unit physical-down vector with its original sensor-event
timestamp in nanoseconds and clock domain `elapsedRealtimeNanos`, or an
unavailable reason. Preserve the sample time, not the callback-receipt time;
[Android's sensor timestamp contract](https://developer.android.com/reference/android/hardware/SensorEvent#timestamp)
defines this monotonic timebase. Device axes mean x-right, y-toward the
phone's natural top, z-out of the screen. For a stationary upright reading
`(0,+g,0)`, physical down in these device axes is `(0,-1,0)`; it becomes
`(0,+1,0)` only after conversion to the appropriate upright camera/view axes.
The consumer owns that conversion and must not invert physical direction again.

**Edge assertion:** feed known stationary raw samples through both provider and
capture transform. Upright camera-frame down must be `(0,1,0)`; a camera looking
straight down has positive gravity z. Missing data disables gravity solving.

## Acceptance checks

- Test raw sensor samples for upright, screen-up flat, screen-down flat, both
  landscape orientations, and a 30-degree tilt. Derive expectations from pose,
  not from the implementation's own transform output.
- An upright portrait sample must yield level-overlay pitch/roll `(0,0)` within
  `1e-6` degrees. Repeat with display rotations 90, 180, and 270 degrees and
  corresponding physical poses; a reversed portrait phone must also be handled.
- Check finite normalization, zero vector, NaN, infinity, sensor absence, and
  initialization before the first reading. Unavailable is not "level".
- Replace old landscape and zero-vector expectations where they encode the
  previous sign/fallback behavior. Run raw-sample-to-overlay checks for both
  landscape poses and assert unavailable presentation before the first sample.
- Confirm diagnostic pitch is negative when looking down and positive when
  looking up, independently of the overlay's numeric convention.
- Add an Android/provider integration test so pure geometry tests cannot pass
  while the sensor adapter still sends the opposite direction.

## Build history


### Verified run 2026-09-19T19:15:26Z

PIDs are historical; all workers have stopped and file reservations are released. Verification-only attempts reuse the completed worker identity. [Run report](../reviews/graph-loop-20260919T191526Z.md) records the evidence and limits.

| Task / attempt | Worker PID | Start UTC | End UTC | Outcome |
| --- | --- | --- | --- | --- |
| gravity07/1 | 226656 | 2026-09-19T13:45:10Z | 2026-09-19T14:02:12Z | passed |
