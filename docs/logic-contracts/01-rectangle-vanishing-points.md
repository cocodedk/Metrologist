---
id: N01
type: work
spec_status: ready
build_status: built
build_agent: "rectangle01/2"
build_pid: 426681
build_pid_note: recorded
build_attempt: 2
build_started_at: "2026-09-19T17:23:40Z"
build_ended_at: "2026-09-19T19:15:26Z"
build_verified_at: "2026-09-19T19:15:26Z"
build_requested_action: none
build_request_id: 0
build_acknowledged_request_id: 0
build_heartbeat_at: "2026-09-19T17:31:07Z"
build_files: []
build_last_error: null
build_evidence: ["docs/reviews/graph-loop-20260919T191526Z.md", ".supervisor/reports/integration-6/manifest.json", ".supervisor/rectangle01-2-check.log", ".supervisor/graph-records/20260919T191526Z/state.json"]
priority: high
finding: F1
tags: [logic-contracts]
---

# Rectangle vanishing points

## Problem and evidence

`RectangleSolver.solve` rejects a rectangle whenever either opposite-edge pair
is parallel in the image. A vanishing point at infinity is a valid direction,
not necessarily a degenerate plane. The resulting gravity fallback assumes a
wall facing the camera and can distort a sideways view.

The review's exact synthetic 2 by 1 m wall at 45 degrees yaw and zero pitch
returned approximately 2.04 by 1.43 m with 70% confidence.

## Required changes

- Preserve vanishing directions in homogeneous form. Back-project them without
  requiring division by their homogeneous third coordinate.
- Support one or both vanishing points at infinity, including pure yaw, pure
  pitch, and an exactly frontal rectangle.
- Distinguish a point at infinity from a zero homogeneous vector, coincident
  directions, or a plane whose projection is numerically unusable.
- Return conditioning and rectangle-consistency evidence for selection. A
  computable normal alone does not establish a rectangular target.
- Remove tests that require valid infinity cases to return null; replace those
  expectations with measurements against independent ground truth.

## Code and test touchpoints

- [RectangleSolver.kt](../../app/src/main/java/com/cocode/measureapp/geometry/RectangleSolver.kt)
- [Projective.kt](../../app/src/main/java/com/cocode/measureapp/geometry/Projective.kt)
- [RectangleSolverTest.kt](../../app/src/test/java/com/cocode/measureapp/geometry/RectangleSolverTest.kt)
- [SyntheticScene.kt](../../app/src/test/java/com/cocode/measureapp/geometry/SyntheticScene.kt)
- [MetrologyEngineGravityOracleTest.kt](../../app/src/test/java/com/cocode/measureapp/geometry/MetrologyEngineGravityOracleTest.kt)

## Incoming contracts

- [[02-capture-coordinate-frame#C03 Aligned projection]] supplies consistent pixels and intrinsics.
- [[06-marker-geometry-validation#C06 Valid object quad]] supplies validated object corners.

## Outgoing contracts

### C07 Plane candidate

**Consumer:** [[03-solver-eligibility-confidence]].

Provide either a finite orthonormal plane candidate with conditioning and
rectangle-consistency evidence, or an explicit rejection reason. Direction at
infinity must not itself cause rejection. The consumer must still apply its
eligibility rules; it must never convert a rejected candidate into success by
assigning it a nonzero confidence.
Preserve the aligned scene identity and calibration provenance with the
candidate. Node 03 applies the selected-surface check when gravity is available;
this node must not claim that a computable plane proves wall/floor consistency.

**Edge assertion:** send exact frontal, pure-yaw, and pure-pitch rectangles
through the real selector and engine. Valid cases select an eligible rectangle
solution and recover the oracle dimensions; collapsed directions are rejected.

## Acceptance checks

Use `SyntheticScene`: width 2 m, height 1 m, stick 1 by 0.04 m,
translation `(0, 0, 4)`, intrinsics `(1000, 1000, 1000, 750)`.

- Test yaw 0, 30, 45, and 60 degrees with pitch 0; also pure pitch 30 degrees
  and combined yaw 25 / pitch 20 degrees. Exact dimensions, area, and diagonal
  must match truth within relative tolerance `1e-6`.
- Check finite unit axes, mutual orthogonality, and finite corner angles.
- Perturb marks around parallel-edge cases to test continuity; record a
  justified conditioning threshold and reject unstable cases instead of
  claiming accuracy from their visually square image angles.
- Include node 03's explicit half-pixel positive fixture and 5% focal-error
  cases in the selector/engine integration. Exact-oracle success alone is not
  sufficient to choose a conditioning or eligibility threshold.
- Keep degenerate-input rejection tests. Coordinate transforms and solver
  choice policy belong to their linked nodes.

## Build history


### Verified run 2026-09-19T19:15:26Z

PIDs are historical; all workers have stopped and file reservations are released. Verification-only attempts reuse the completed worker identity. [Run report](../reviews/graph-loop-20260919T191526Z.md) records the evidence and limits.

| Task / attempt | Worker PID | Start UTC | End UTC | Outcome |
| --- | --- | --- | --- | --- |
| rectangle01/1 | 320909 | 2026-09-19T14:44:37Z | 2026-09-19T15:03:15Z | failed |
| rectangle01/2 | 426681 | 2026-09-19T17:23:40Z | 2026-09-19T17:31:45Z | passed |
