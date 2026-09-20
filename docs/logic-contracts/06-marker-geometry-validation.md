---
id: N06
type: work
spec_status: ready
build_status: built
build_agent: "markers06/1"
build_pid: 226630
build_pid_note: recorded
build_attempt: 1
build_started_at: "2026-09-19T13:45:10Z"
build_ended_at: "2026-09-19T19:15:26Z"
build_verified_at: "2026-09-19T19:15:26Z"
build_requested_action: none
build_request_id: 0
build_acknowledged_request_id: 0
build_heartbeat_at: "2026-09-19T14:00:20Z"
build_files: []
build_last_error: null
build_evidence: ["docs/reviews/graph-loop-20260919T191526Z.md", ".supervisor/reports/integration-6/manifest.json", ".supervisor/markers06-1-check.log", ".supervisor/graph-records/20260919T191526Z/state.json"]
priority: medium
finding: F6
tags: [logic-contracts]
---

# Marker geometry validation

## Problem and evidence

`CornerOrdering.order` only requires four distinct points. Four collinear points
pass that check and can produce zero area with 100% confidence. Stick corners
are sent to the engine without equivalent UI validation.

## Required changes

- Build reusable pure validation for the app's object and stick quadrilaterals.
  Check point count, finite coordinates, distinct vertices, nonzero edge lengths,
  nonzero area, and supported polygon shape before projection.
- Normalize arbitrary object tap order into the existing clockwise convention,
  then check convexity. A valid convex nonrectangle must remain valid input;
  rectangle eligibility belongs to node 03.
- Reject a concave or collinear target with an explicit explanation unless
  support is deliberately implemented and verified. Do not silently replace
  an invalid point with another corner or fabricate a bounding rectangle.
- Preserve cyclic stick-edge relationships when validating its four-corner
  box. Crossed or collapsed app markers require correction, not a guessed width.
  Accept either clockwise or counter-clockwise winding. Object canonicalization
  must not impose its clockwise requirement on stick input; preserve opposite
  edges and the physical length/width axes if a consumer normalizes winding.
- Use named, scale-aware degeneracy tolerances and document the coordinate
  space they apply to. Zooming the view must not change validation outcomes.
- Validate again at the engine boundary so non-UI callers cannot bypass the
  guarantees. Share validation logic rather than duplicating its rules.

## Code and test touchpoints

- [CornerOrdering.kt](../../app/src/main/java/com/cocode/measureapp/core/CornerOrdering.kt)
- [MarkScreen.kt](../../app/src/main/java/com/cocode/measureapp/ui/MarkScreen.kt)
- [Measurements.kt](../../app/src/main/java/com/cocode/measureapp/geometry/Measurements.kt)
- [StickBox.kt](../../app/src/main/java/com/cocode/measureapp/stick/StickBox.kt)
- [Tolerances.kt](../../app/src/main/java/com/cocode/measureapp/geometry/Tolerances.kt)
- [CornerOrderingTest.kt](../../app/src/test/java/com/cocode/measureapp/core/CornerOrderingTest.kt)

## Incoming contracts

None. This node validates user marks in the canonical image-pixel frame defined
by the graph's shared convention; it does not perform camera transformations.

## Outgoing contracts

### C06 Valid object quad

**Consumer:** [[01-rectangle-vanishing-points]].

Supply four finite, distinct, clockwise object corners with positive area and
supported convex topology, or reject before solving. This guarantee says the
quad is valid input, not that its real-world corners are right angles.

**Edge assertion:** shuffled square and convex nonrectangle inputs reach the
solver in canonical order; duplicate, collinear, and concave inputs do not.

### C08 Validation outcome

**Consumer:** [[05-measurement-failure-handling]].

Return accepted marks or a structured rejection identifying object/stick and
the failed rule. The consumer preserves placements, explains the correction,
and prevents a failed validation from opening Results or enabling Export.

**Edge assertion:** collinear object marks and a collapsed stick yield different
actionable errors in the marking UI; fixing them permits a new attempt.

### C13 Valid stick quad

**Consumer:** [[03-solver-eligibility-confidence]].

The app's reference geometry is a valid four-corner box with intact opposite
edge relationships. It is not a centreline or the detector's five band points.
Both windings are accepted, including the detector-created counter-clockwise
box. Winding normalization must preserve the same physical edge pairs and scale.
The consumer must additionally validate the projected geometry before scale
recovery; a valid image quad can still meet a projection singularity.

**Edge assertion:** valid horizontal, vertical, and perspective stick boxes
reach scale recovery in both windings with equal dimensions; collapsed or
self-crossed app boxes are rejected before division.

## Acceptance checks

- Reject `(800,600)`, `(900,600)`, `(1000,600)`, `(1100,600)`. They must never
  produce a usable zero-area result, regardless of confidence or gravity.
- Test duplicate vertices, three collinear adjacent vertices, a point inside
  the other three, non-finite coordinates, and an invalid cyclic stick order.
- Keep shuffled, diamond-oriented, thin-but-resolved, and convex nonrectangular
  valid cases. Test both sides of the documented degeneracy tolerance.
- Check the same marks under different display zoom factors: validation uses
  image coordinates and returns the same outcome.
- Use the detector-style cyclic box `(100,110)`, `(300,110)`, `(300,90)`,
  `(100,90)`, its reverse and cyclic shifts. Each must yield identical scale
  and dimensions within relative `1e-9`; swapping only the middle two vertices
  creates a crossed box and must be rejected. Include the actual auto-detection
  handoff so tests do not cover only manually placed clockwise boxes.
- Keep low-level length-only scale compatibility distinct from the app's valid
  four-corner marking contract; a zero profile width does not repair bad UI marks.

## Build history


### Verified run 2026-09-19T19:15:26Z

PIDs are historical; all workers have stopped and file reservations are released. Verification-only attempts reuse the completed worker identity. [Run report](../reviews/graph-loop-20260919T191526Z.md) records the evidence and limits.

| Task / attempt | Worker PID | Start UTC | End UTC | Outcome |
| --- | --- | --- | --- | --- |
| markers06/1 | 226630 | 2026-09-19T13:45:10Z | 2026-09-19T14:00:56Z | passed |
