# Combined integration task

This is the final verification task's own specification. Build and run real
Android flow assertions for these 15 contracts on the combined implementation.
Use the named required test class and extract supporting tests/helpers within
the reserved contracts directory to stay below 200 lines per code file.
Do not open other graph notes; the necessary edge contracts are included here.
Each edge needs a test method prefixed by its Cxx_ identifier. Report any
unavailable device or physical-camera checks as limitations.

## Calibration page regression (user-requested)

The coordinator rebuilt `tools/calibration-target.html` and rendered its actual
SVG artwork into `app/src/androidTest/assets/calibration-target/`. Read the
`frontal.json` and `nonrectangle.json` fixture metadata there. Each corresponding
PNG is the actual page target at 3 pixels/mm, with a 23 x 15 cm bounding box and
a 10 x 2 cm reference. The JSON contains independent polygon ground truth,
pixel coordinates, synthetic intrinsics and physical-down direction.

Use these retained assets in an Android Compose marking flow regression within
your reserved contracts directory. Load the PNG and metadata using the test
context's assets. Exercise the real marking/measurement callback, presenter and
results for the frontal rectangle and nonrectangle; compare width, height,
area and angles against the JSON (relative tolerance 1e-6 for numeric engine
outputs, normal display rounding for UI text). The nonrectangle must not become
a high-confidence rectangle or lose its non-right angles. Re-mark and change
surface to verify stale results cannot survive. Integrate this with C05/C07/C10
where practical. Do not alter the fixture values to match the solver.

These are rendered images with synthetic camera metadata, NOT a physical-camera
test. Preserve this limitation in the report. The test emulator is already
booted; the supervisor selects it explicitly for the verification command.
The coordinator retains actual browser checks and screenshots separately.

Keep the final structured report compact (roughly 120 words plus file paths).
Do not invoke the provider-side `advisor` tool or any additional review agent.
Its output is opaque in this CLI and its wait cannot be separately supervised.
Review your own changes locally; the coordinator runs and inspects verification.

## C01 Complete capture

**Consumer:** 02-capture-coordinate-frame.

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

## C02 Downward vector

**Consumer:** 02-capture-coordinate-frame.

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

## C03 Aligned projection

**Consumer:** 01-rectangle-vanishing-points.

Object/stick pixels and intrinsics must describe the canonical marking bitmap.
Preserve image identity across marking and measurement. A missing or invalid
transform must prevent a calibrated rectangle candidate, not produce a guessed
high-confidence one. Keep calibration quality/source attached to that scene:
`calibrated`, `approximate`, or `unavailable`, with the origin of the intrinsics.

**Edge assertion:** rotate the same synthetic capture through 0, 90, 180, and
270 degrees and apply a known crop/resize. After alignment, corresponding rays
match within `1e-9`, and recovered dimensions match within relative `1e-6`.

## C04 Aligned gravity

**Consumer:** 03-solver-eligibility-confidence.

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

## C05 Explicit surface

**Consumer:** 03-solver-eligibility-confidence.

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

## C06 Valid object quad

**Consumer:** 01-rectangle-vanishing-points.

Supply four finite, distinct, clockwise object corners with positive area and
supported convex topology, or reject before solving. This guarantee says the
quad is valid input, not that its real-world corners are right angles.

**Edge assertion:** shuffled square and convex nonrectangle inputs reach the
solver in canonical order; duplicate, collinear, and concave inputs do not.

## C07 Plane candidate

**Consumer:** 03-solver-eligibility-confidence.

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

## C08 Validation outcome

**Consumer:** 05-measurement-failure-handling.

Return accepted marks or a structured rejection identifying object/stick and
the failed rule. The consumer preserves placements, explains the correction,
and prevents a failed validation from opening Results or enabling Export.

**Edge assertion:** collinear object marks and a collapsed stick yield different
actionable errors in the marking UI; fixing them permits a new attempt.

## C09 Valid settings

**Consumer:** 05-measurement-failure-handling.

Supply validated app settings, or an explicit requirement to correct the length
or width. The consumer must prevent measurement with invalid settings while
preserving the current image and marks. Invalid values are never formatted as
accepted settings or sent through a successful measurement path.

**Edge assertion:** enter or load each invalid value, then attempt measurement.
The app explains which dimension needs correction and does not crash. Replace
the value with a valid one and retry using the same image.

## C10 Measurement outcome

**Consumer:** 05-measurement-failure-handling.

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

## C11 Recoverable retake

**Consumer:** 08-capture-recovery.

When the user requests Retake after a failed measurement, return to a camera
state that can accept a new capture. Invalidate the old result and its export
eligibility. Do not carry a measurement error into the new capture's busy state;
the camera node owns its own in-flight operation and resource cleanup. Retake
uses this node's shared invalidation operation before accepting a new image.

**Edge assertion:** fail measurement, choose Retake, and successfully capture a
new image. No restart is required and no result from the old image is displayed.

## C12 Metric profile

**Consumer:** 03-solver-eligibility-confidence.

Provide a finite strictly positive reference length in metres and either a
finite strictly positive known width, or the explicitly supported engine-only
unknown-width value zero. Negative, NaN, and infinite widths are always invalid.
The consumer must still guard arithmetic overflow in scale/measurement output;
valid finite inputs alone do not guarantee finite intermediate calculations.

**Edge assertion:** equivalent valid profiles entered in different units yield
the same metric results. Direct engine callers with invalid dimensions receive
a rejected outcome; a supported length-only caller with zero width still works.

## C13 Valid stick quad

**Consumer:** 03-solver-eligibility-confidence.

The app's reference geometry is a valid four-corner box with intact opposite
edge relationships. It is not a centreline or the detector's five band points.
Both windings are accepted, including the detector-created counter-clockwise
box. Winding normalization must preserve the same physical edge pairs and scale.
The consumer must additionally validate the projected geometry before scale
recovery; a valid image quad can still meet a projection singularity.

**Edge assertion:** valid horizontal, vertical, and perspective stick boxes
reach scale recovery in both windings with equal dimensions; collapsed or
self-crossed app boxes are rejected before division.

## C14 Shared outcome schema

**Consumer:** 03-solver-eligibility-confidence.

This node owns the pure success/failure type and stable failure-reason vocabulary
shared by engine and presenter. Success carries the finite measurement, solver,
assumptions and quality evidence; failure has a reason and no usable measurement.
Node 03 implements eligibility and produces this type, rather than inventing
a parallel result wrapper. Schema changes must update producer and presenter
together; no coercion of failure to a zero-valued success is permitted.

**Edge assertion:** feed node 03's real success and failure variants through the
presenter using this schema; failures retain their reason and cannot enable export.

## C15 Result invalidation

**Consumer:** 04-surface-orientation.

Provide one invalidation operation for the current image/input revision. A
surface change invokes it before another measurement; it clears usable results
and export eligibility while retaining photo, marks and the new orientation.
Completion from an older revision cannot restore a stale result.

**Edge assertion:** measure successfully, change orientation, and deliver an old
completion. Results/export stay unavailable until the new revision succeeds.
