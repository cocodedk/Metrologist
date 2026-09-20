---
id: N04
type: work
spec_status: ready
build_status: built
build_agent: "surface04/1"
build_pid: 272839
build_pid_note: recorded
build_attempt: 1
build_started_at: "2026-09-19T14:13:28Z"
build_ended_at: "2026-09-19T19:15:26Z"
build_verified_at: "2026-09-19T19:15:26Z"
build_requested_action: none
build_request_id: 0
build_acknowledged_request_id: 0
build_heartbeat_at: "2026-09-19T14:24:47Z"
build_files: []
build_last_error: null
build_evidence: ["docs/reviews/graph-loop-20260919T191526Z.md", ".supervisor/reports/integration-6/manifest.json", ".supervisor/surface04-1-check.log", ".supervisor/graph-records/20260919T191526Z/state.json"]
priority: high
finding: F4
tags: [logic-contracts]
---

# Surface orientation

## Problem and evidence

`MarkScreen` hardcodes `SurfaceOrientation.VERTICAL` for every measurement.
The engine supports horizontal floors and tables, but the app cannot request
that branch. The review's 2 by 1 m floor fixture succeeded in horizontal mode
and became unusable with the screen's wall-mode input.

## Required changes

- Add an understandable wall versus floor/table choice in the marking flow.
  If wall is initially selected, it must be visibly selected; the assumption
  must not be hidden in an engine call.
- Pass the selected value through the presenter into the hybrid engine.
- Keep orientation with the current measurement's state, alongside its image
  and marks, so Results -> Re-mark retains the choice.
- Call node 05's shared invalidation operation when orientation changes; the
  displayed/exported measurement must use the current selection and revision.
- Keep camera pose and surface orientation separate. A downward-looking camera
  does not prove that the target is horizontal.

## Code and test touchpoints

- [MarkScreen.kt](../../app/src/main/java/com/cocode/measureapp/ui/MarkScreen.kt)
- [MeasureApp.kt](../../app/src/main/java/com/cocode/measureapp/ui/MeasureApp.kt)
- [MeasurementPresenter.kt](../../app/src/main/java/com/cocode/measureapp/core/MeasurementPresenter.kt)
- [GravitySolver.kt](../../app/src/main/java/com/cocode/measureapp/geometry/GravitySolver.kt)
- [MetrologyEngineHorizontalHybridTest.kt](../../app/src/test/java/com/cocode/measureapp/geometry/MetrologyEngineHorizontalHybridTest.kt)

Build a UI test for selecting floor/table and re-marking. The existing horizontal
engine test alone cannot verify that the real screen supplies the selected enum.

## Incoming contracts

- [[05-measurement-failure-handling#C15 Result invalidation]] supplies the shared state transition for a changed selection.

This node originates the user's surface assumption and follows the map's units.

## Outgoing contracts

### C05 Explicit surface

**Consumer:** [[03-solver-eligibility-confidence]].

Every measurement request includes the currently visible surface selection.
The consumer must use that orientation for gravity eligibility and solving,
and for checking rectangle-normal consistency when valid gravity is present;
it must not replace it with `VERTICAL`, infer it from screen rotation, or carry
it over from a stale result. Node 03 owns the common 5-degree surface-consistency
rule; the rectangle branch cannot bypass a known contradiction. Without usable
gravity, a geometric rectangle may succeed with surface consistency explicitly
unverified. Do not pretend the UI selection independently verifies the plane.

**Edge assertion:** select floor/table in the real marking flow and inspect the
engine request; it must contain `HORIZONTAL`. Select wall and verify `VERTICAL`.
Return through Results -> Re-mark and assert the value is unchanged. Change it
and verify that any subsequent result is newly computed for the changed value.

## Acceptance checks

- UI coverage exercises both selections and the re-mark navigation path.
- A positive horizontal-plane oracle recovers known dimensions and angles.
  Include a convex nonrectangular floor so the repaired rectangle solver cannot
  mask whether the horizontal gravity path is actually reachable.
- The same floor with an incompatible wall assumption cannot silently display
  the previously computed floor result.
- Existing wall measurement remains usable. Do not add a sloped-surface mode:
  a known slope inconsistent with wall/floor requires rejection; without gravity,
  a geometric rectangle may use the selector's explicit unverified-surface path.

## Build history


### Verified run 2026-09-19T19:15:26Z

PIDs are historical; all workers have stopped and file reservations are released. Verification-only attempts reuse the completed worker identity. [Run report](../reviews/graph-loop-20260919T191526Z.md) records the evidence and limits.

| Task / attempt | Worker PID | Start UTC | End UTC | Outcome |
| --- | --- | --- | --- | --- |
| surface04/1 | 272839 | 2026-09-19T14:13:28Z | 2026-09-19T14:30:25Z | passed |
