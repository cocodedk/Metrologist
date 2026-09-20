---
id: N05
type: work
spec_status: ready
build_status: built
build_agent: "recovery05/1"
build_pid: 487257
build_pid_note: recorded
build_attempt: 2
build_started_at: "2026-09-19T18:00:48Z"
build_ended_at: "2026-09-19T19:15:26Z"
build_verified_at: "2026-09-19T19:15:26Z"
build_requested_action: none
build_request_id: 0
build_acknowledged_request_id: 0
build_heartbeat_at: "2026-09-19T18:17:10Z"
build_files: []
build_last_error: null
build_evidence: ["docs/reviews/graph-loop-20260919T191526Z.md", ".supervisor/reports/integration-6/manifest.json", ".supervisor/recovery05-1-check.log", ".supervisor/graph-records/20260919T191526Z/state.json"]
priority: medium
finding: F5
tags: [logic-contracts]
---

# Measurement failure handling

## Problem and evidence

The marking screen catches failures from corner ordering but calls the presenter
and engine without a recoverable error boundary. A collapsed stick box with a
known width throws `IllegalArgumentException` from `StickScale.solve`.

## Required changes

- Consume the explicit validation and measurement outcomes defined by the
  incoming contracts. Expected input errors must stay in the marking flow.
- Give a specific correction message for invalid object corners, invalid stick
  corners, invalid reference dimensions, unavailable metadata, unsupported
  geometry, and numerical failure. Keep the photo and existing placements.
- Define and own the shared pure outcome model before node 03 implements its
  producer. Node 03 populates this type; the presenter consumes it. Do not hide
  failure in fabricated `MeasurementResult(0, ...)` values or in a confidence
  percentage that the UI may mistake for success.
- Handle expected numerical/domain errors at their boundary. Avoid catching
  every `Throwable`, suppressing coroutine cancellation, or disguising coding
  defects as successful low-confidence measurements.
- A failed attempt must invalidate any older result. Never display or export
  the preceding successful measurement after the user changes input and fails.
- Own one result-invalidation operation at the `MeasureApp` state boundary.
  Mark edits, settings changes, surface changes and Retake call this operation;
  nodes 04 and 08 must not introduce independent stale-result policies. Bind a
  successful result to its image/input revision and reject late older results.
- Allow correction and a new attempt without restarting or recapturing; keep
  Retake available when the current image cannot support measurement.

## Code and test touchpoints

- [MarkScreen.kt](../../app/src/main/java/com/cocode/measureapp/ui/MarkScreen.kt)
- [MeasureApp.kt](../../app/src/main/java/com/cocode/measureapp/ui/MeasureApp.kt)
- [MeasurementPresenter.kt](../../app/src/main/java/com/cocode/measureapp/core/MeasurementPresenter.kt)
- [MetrologyEngine.kt](../../app/src/main/java/com/cocode/measureapp/geometry/MetrologyEngine.kt)
- [StickScale.kt](../../app/src/main/java/com/cocode/measureapp/stick/StickScale.kt)
- [ResultsScreen.kt](../../app/src/main/java/com/cocode/measureapp/ui/ResultsScreen.kt)
- [MeasurementPresenterTest.kt](../../app/src/test/java/com/cocode/measureapp/core/MeasurementPresenterTest.kt)

Build marking-flow tests for rejection, correction, successful retry, and export
state. The validator owns the geometry rules; this node owns the response to them.

## Incoming contracts

- [[06-marker-geometry-validation#C08 Validation outcome]] identifies invalid markers.
- [[09-reference-dimensions#C09 Valid settings]] supplies valid settings or a correction requirement.
- [[03-solver-eligibility-confidence#C10 Measurement outcome]] distinguishes success from failure.

## Outgoing contracts

### C11 Recoverable retake

**Consumer:** [[08-capture-recovery]].

When the user requests Retake after a failed measurement, return to a camera
state that can accept a new capture. Invalidate the old result and its export
eligibility. Do not carry a measurement error into the new capture's busy state;
the camera node owns its own in-flight operation and resource cleanup. Retake
uses this node's shared invalidation operation before accepting a new image.

**Edge assertion:** fail measurement, choose Retake, and successfully capture a
new image. No restart is required and no result from the old image is displayed.

### C14 Shared outcome schema

**Consumer:** [[03-solver-eligibility-confidence]].

This node owns the pure success/failure type and stable failure-reason vocabulary
shared by engine and presenter. Success carries the finite measurement, solver,
assumptions and quality evidence; failure has a reason and no usable measurement.
Node 03 implements eligibility and produces this type, rather than inventing
a parallel result wrapper. Schema changes must update producer and presenter
together; no coercion of failure to a zero-valued success is permitted.

**Edge assertion:** feed node 03's real success and failure variants through the
presenter using this schema; failures retain their reason and cannot enable export.

### C15 Result invalidation

**Consumer:** [[04-surface-orientation]].

Provide one invalidation operation for the current image/input revision. A
surface change invokes it before another measurement; it clears usable results
and export eligibility while retaining photo, marks and the new orientation.
Completion from an older revision cannot restore a stale result.

**Edge assertion:** measure successfully, change orientation, and deliver an old
completion. Results/export stay unavailable until the new revision succeeds.

## Acceptance checks

- Reproduce the collapsed stick with image corners `(875,750)`, `(1125,750)`,
  `(1125,750)`, `(875,750)`, known length 1 m and width 0.04 m. A valid object
  quad must not prevent this stick error from being caught and explained.
- Send a ray-parallel-to-plane case and an unusable-solver case through the
  engine and presenter. Both must return failures without leaking domain errors.
- After a success, invalidate the marks and measure again: there must be no
  stale usable result or enabled export path. Correct the marks and verify success.
- Rejecting bad settings must not lose the captured photo or the orientation
  selected for it. Back, Re-mark, and Retake remain functional.
- Validate failure behavior through the actual UI callback path, not only by
  asserting that low-level helpers throw the expected exception.

## Build history


### Verified run 2026-09-19T19:15:26Z

PIDs are historical; all workers have stopped and file reservations are released. Verification-only attempts reuse the completed worker identity. [Run report](../reviews/graph-loop-20260919T191526Z.md) records the evidence and limits.

| Task / attempt | Worker PID | Start UTC | End UTC | Outcome |
| --- | --- | --- | --- | --- |
| schema05/1 | 202530 | 2026-09-19T13:39:27Z | 2026-09-19T13:45:10Z | passed |
| recovery05/1 | 487257 | 2026-09-19T18:00:48Z | 2026-09-19T18:17:46Z | passed |
