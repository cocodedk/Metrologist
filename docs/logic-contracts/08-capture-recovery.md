---
id: N08
type: work
spec_status: ready
build_status: built
build_agent: "capture08/1"
build_pid: 254201
build_pid_note: recorded
build_attempt: 1
build_started_at: "2026-09-19T14:02:12Z"
build_ended_at: "2026-09-19T19:15:26Z"
build_verified_at: "2026-09-19T19:15:26Z"
build_requested_action: none
build_request_id: 0
build_acknowledged_request_id: 0
build_heartbeat_at: "2026-09-19T14:12:29Z"
build_files: []
build_last_error: null
build_evidence: ["docs/reviews/graph-loop-20260919T191526Z.md", ".supervisor/reports/integration-6/manifest.json", ".supervisor/capture08-1-check.log", ".supervisor/graph-records/20260919T191526Z/state.json"]
priority: medium
finding: F8
tags: [logic-contracts]
---

# Capture recovery

## Problem and evidence

The Capture button sets `capturing = true`, but only the success callback resets
it. `onError` shows a toast without completing the screen's busy state. A camera
failure therefore leaves retry disabled until the screen is reopened.

## Required changes

- Give every accepted capture request one terminal outcome: success, failure,
  or cancellation. Clear busy state for all three while the owning screen is active.
- Surface a useful retry message and re-enable capture after failure, provided
  permission is granted and the camera is ready.
- Include conversion/metadata exceptions and a synchronous capture-start failure
  in the recovery path; a camera callback error is not the only way to fail.
- Close an acquired `ImageProxy` even when bitmap conversion or metadata
  extraction fails. Copy required metadata before closing it.
- Prevent duplicate captures while one is active. Ignore late results belonging
  to a disposed screen or superseded request, without leaking their resources.
- Keep the capture lifecycle independent of measurement failures. Retake
  initializes a usable camera flow rather than inheriting an old busy flag.
  Use node 05's shared result-invalidation operation through C11; this node
  owns capture cleanup, not a separate policy for stale measurement results.

## Code and test touchpoints

- [CameraHelpers.kt](../../app/src/main/java/com/cocode/measureapp/ui/CameraHelpers.kt)
- [CameraScreen.kt](../../app/src/main/java/com/cocode/measureapp/ui/CameraScreen.kt)
- [MeasureApp.kt](../../app/src/main/java/com/cocode/measureapp/ui/MeasureApp.kt)
- [CapturedImage.kt](../../app/src/main/java/com/cocode/measureapp/ui/CapturedImage.kt)

Build a testable capture callback/state boundary and Android flow tests. Do not
require physical camera failure to exercise retry, cleanup, and stale callbacks.

## Incoming contracts

- [[05-measurement-failure-handling#C11 Recoverable retake]] requests a fresh capture after measurement failure.

## Outgoing contracts

### C01 Complete capture

**Consumer:** [[02-capture-coordinate-frame]].

A successful capture delivers one image with the metadata identifying that
same shot, including the rotation/crop information needed for alignment.
Include exposure timestamp in nanoseconds and the camera timestamp-source
declaration, or explicit unavailability. Copy their shot identity and provenance
before proxy closure; callback time must not be substituted for exposure time.
Required metadata must survive proxy closure. An incomplete image/metadata
handoff is a failure, not a partial success; unavailable optional gravity or
calibration data is explicitly flagged. The consumer must not assemble a scene
using an image from one request and metadata from another.

**Edge assertion:** simulate success, camera error, conversion failure, and a
late callback after Retake/navigation. Only the active successful request reaches
alignment, with matching image identity. All paths release acquired proxies;
failure restores the active screen's retry state.
Include REALTIME, UNKNOWN and unavailable capture timestamps; optional timestamp
unavailability is preserved for node 02 to disable gravity, not guessed here.

## Acceptance checks

| Event | Required visible/state outcome |
| --- | --- |
| Ready camera, capture starts | Busy; second request blocked |
| Capture succeeds | Busy cleared; one matching image reaches Mark |
| Camera reports error | Error shown; retry enabled when camera is ready |
| Conversion or metadata extraction fails | Proxy closed; error and retry available |
| Capture start throws | Busy cleared; no fabricated success |
| User leaves while capture is pending | Late callback cannot navigate the new screen |
| Failed measurement followed by Retake | Fresh capture succeeds without restart |

Verify failure followed by success in one screen instance. Assertions must cover
the actual state that enables the button, not merely the presence of an error toast.

## Build history


### Verified run 2026-09-19T19:15:26Z

PIDs are historical; all workers have stopped and file reservations are released. Verification-only attempts reuse the completed worker identity. [Run report](../reviews/graph-loop-20260919T191526Z.md) records the evidence and limits.

| Task / attempt | Worker PID | Start UTC | End UTC | Outcome |
| --- | --- | --- | --- | --- |
| capture08/1 | 254201 | 2026-09-19T14:02:12Z | 2026-09-19T14:13:28Z | passed |
