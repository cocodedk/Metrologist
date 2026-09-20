# Concrete producer handoff

Node 01 now exposes `RectangleSolver.candidate`: a finite orthonormal plane plus
rectangle/conditioning evidence and calibration provenance, or a typed rejection.
The legacy `solve` method still rejects image-parallel edges. Wire the real
selection/engine path to the candidate API in this milestone; the old behavior
is not a compatibility requirement. Preserve independent oracle accuracy.

`CapturedScene.alignedGravity` carries validity and absence. Its legacy `gravity`
field can contain a placeholder, so the new production entry point must consume
the typed value and preserve scene calibration and input revision. Node 05 will
wire the presenter and UI to your explicit `MeasurementOutcome` producer.
State that entry point and its arguments clearly in your completion summary.
Do not invent another outcome schema. Existing `MeasurementDiagnostics` is
declared in your reserved `MetrologyEngine.kt`; it may carry additional evidence.

Legacy tests expecting infinity rejection or always choosing gravity need
contract-based correction. Additional directly affected tests are reserved.
Extract test helpers under `geometry/eligibility`, and ensure the required
`SolverEligibilityContractTest` actually invokes all acceptance cases.

The shared schema, scene alignment, marker validation, and dimension validation
are implemented. Inspect their source interfaces as needed. The node-reading
limit still applies. Your own original specification remains authoritative.
Downstream presenter/UI work belongs in handoff notes, not in `blockers`, unless
it genuinely prevents this milestone's producer from working or compiling.

Keep the final structured report compact: summary at most 120 words, tests as
short identifiers, and blockers only when truly blocking. Source and tool logs
already preserve implementation detail. Do not repeat the report as long prose.
