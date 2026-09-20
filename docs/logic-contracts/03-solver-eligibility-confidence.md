---
id: N03
type: work
spec_status: ready
build_status: built
build_agent: "eligibility03/1"
build_pid: 445056
build_pid_note: recorded
build_attempt: 1
build_started_at: "2026-09-19T17:31:45Z"
build_ended_at: "2026-09-19T19:15:26Z"
build_verified_at: "2026-09-19T19:15:26Z"
build_requested_action: none
build_request_id: 0
build_acknowledged_request_id: 0
build_heartbeat_at: "2026-09-19T18:00:10Z"
build_files: []
build_last_error: null
build_evidence: ["docs/reviews/graph-loop-20260919T191526Z.md", ".supervisor/reports/integration-6/manifest.json", ".supervisor/eligibility03-1-check.log", ".supervisor/graph-records/20260919T191526Z/state.json"]
priority: high
finding: F3
tags: [logic-contracts]
---

# Solver eligibility and confidence

## Problem and evidence

The selector prefers any rectangle candidate with confidence at least 0.15.
That confidence measures image-space angles, which cannot distinguish target
shape from perspective. It does not verify the rectangle assumption.

A frontal nonrectangular quadrilateral of area 2.32 square metres was reported
as approximately 2.84 square metres with 71% confidence. Its 111.8-degree corner
was reported near 92.2 degrees. A computable plane is not proof of correctness.

## Required changes

- Separate solver eligibility from the confidence score. Reject a candidate
  that contradicts its geometric assumptions even when its image-angle score
  is high. The existing low-confidence fallback must not revive rejected planes.
- Check the orthogonality of back-projected rectangle edge directions before
  constructing an orthogonal basis; use normalized, scale-independent residuals.
  This is a necessary consistency check, not proof that every target is a rectangle.
- Where usable, cross-check the plane with the independently marked reference
  box and its known aspect ratio. Define tolerances with perturbation fixtures;
  do not derive confidence solely from image angles or a chosen solver name.
- Use valid gravity and the selected surface orientation for gravity candidates.
  Gravity alone does not determine a vertical wall's azimuth. Expose that
  assumption and limit confidence or reject results when orientation is unresolved.
- When aligned gravity is valid, cross-check a rectangle candidate's plane
  normal against the selected surface too. For unit normal `n` and unit down
  `g`, wall requires `abs(n dot g) <= sin(5 degrees)`; floor/table requires
  `abs(n dot g) >= cos(5 degrees)`. A contradiction rejects that candidate;
  do not bypass the check by choosing the rectangle branch. Five degrees is
  this repair's explicit consistency tolerance, not a camera-accuracy claim.
  Without usable gravity, an otherwise eligible rectangle remains usable but
  reports surface consistency as unverified; never fabricate a gravity match.
- Preserve genuine nonrectangular shape and angles through a supported gravity
  solution. If neither method is justified, return an explanatory failure.
- Guard projection denominators, consistent depth side, scale, dimensions, area,
  angles, and confidence before formatting. No NaN, infinity, or degenerate
  measurement can be a successful result.

## Code and test touchpoints

- [SolverSelector.kt](../../app/src/main/java/com/cocode/measureapp/geometry/SolverSelector.kt)
- [RectangleSolver.kt](../../app/src/main/java/com/cocode/measureapp/geometry/RectangleSolver.kt)
- [GravitySolver.kt](../../app/src/main/java/com/cocode/measureapp/geometry/GravitySolver.kt)
- [MetrologyEngine.kt](../../app/src/main/java/com/cocode/measureapp/geometry/MetrologyEngine.kt)
- [DiagnosticsText.kt](../../app/src/main/java/com/cocode/measureapp/core/DiagnosticsText.kt)
- [SolverSelectorTest.kt](../../app/src/test/java/com/cocode/measureapp/geometry/SolverSelectorTest.kt)

## Incoming contracts

- [[01-rectangle-vanishing-points#C07 Plane candidate]]: candidate plus consistency evidence.
- [[02-capture-coordinate-frame#C04 Aligned gravity]]: aligned scene and valid gravity or absence.
- [[04-surface-orientation#C05 Explicit surface]]: selected wall/floor assumption.
- [[06-marker-geometry-validation#C13 Valid stick quad]]: real marked reference geometry.
- [[09-reference-dimensions#C12 Metric profile]]: finite dimensions in metres.
- [[05-measurement-failure-handling#C14 Shared outcome schema]]: the one outcome type this node produces.

## Outgoing contracts

### C10 Measurement outcome

**Consumer:** [[05-measurement-failure-handling]].

Return either a successful finite measurement with solver, assumptions, quality
evidence and bounded confidence, or a structured failure with a stable reason.
Use node 05's C14 schema. Carry image/input revision, calibration quality/source
and surface-consistency status through success into diagnostics. An approximate
intrinsics fallback must remain marked approximate even if a solver succeeds.
Expected invalid-input and geometric failures must not escape as uncaught
exceptions. The consumer must not format/export a failed outcome or interpret
placeholder zeros as a measured surface. Node 05 owns presentation and recovery;
this node owns the eligibility decision.

**Edge assertion:** exercise the complete engine-to-presenter path with an
eligible rectangle, a supported gravity surface, both candidates ineligible,
missing gravity, and a projection singularity. Only success yields usable
formatted measurements. Every failure retains an actionable reason.

## Acceptance checks

- Rebuild the nonrectangle fixture using world corners `(-1,-0.5)`, `(1,-0.5)`,
  `(1.4,0.5)`, `(-1,0.6)`, on a frontal wall 4 m away; use intrinsics
  `(1000,1000,1000,750)` and a centred 1 by 0.04 m reference box. Expected
  area is 2.32 square metres. Reject its rectangle candidate; a justified gravity
  result must match independent dimensions/angles within relative `1e-6` /
  absolute `1e-6` degrees, otherwise report the missing assumption explicitly.
- Valid rectangles, including node 01's infinity cases, remain eligible. Test
  residuals on both sides of each documented threshold, not only exact fixtures.
- With the node 01 oracle at yaw 30 / pitch 20 degrees, keep the stick exact
  and offset object corners `[TL, TR, BR, BL]` by `(0.5,0)`, `(0,-0.5)`,
  `(-0.5,0)`, `(0,0.5)` pixels; repeat with all offsets negated. With exact
  intrinsics and gravity, both must remain rectangle-eligible and recover width
  and height within 2% of truth. This tests a nonzero marking-noise floor.
- Separately perturb both focal lengths by +5% and -5%, preserving principal
  point and exact marks, and label the calibration approximate. Return an
  explicitly qualified finite success or a structured rejection; never present
  this as exact calibrated evidence. Do not relax the nonrectangle rejection
  just to pass these cases, or apply exact-oracle tolerances to noisy inputs.
- Exercise rectangle normals 4.9 and 5.1 degrees from the selected wall/floor
  constraint, with otherwise valid inputs: accept and reject respectively.
  A true rectangle on a plane at least 30 degrees from both constraints is rejected
  with valid gravity. With gravity unavailable, its geometric solution may
  succeed only with surface consistency explicitly unverified.
- Test a nonrectangle on a horizontal plane, and a wall with unknown azimuth.
  The latter must not claim high confidence solely because the phone is level.
- Both-candidate failure is supported; the current assumption that gravity
  always produces something selectable must be removed.
- Assert confidence is finite and in `[0,1]`; use of an approximate calibration
  or unresolved plane assumption must remain visible in diagnostics.

## Build history


### Verified run 2026-09-19T19:15:26Z

PIDs are historical; all workers have stopped and file reservations are released. Verification-only attempts reuse the completed worker identity. [Run report](../reviews/graph-loop-20260919T191526Z.md) records the evidence and limits.

| Task / attempt | Worker PID | Start UTC | End UTC | Outcome |
| --- | --- | --- | --- | --- |
| eligibility03/1 | 445056 | 2026-09-19T17:31:45Z | 2026-09-19T18:00:48Z | passed |
