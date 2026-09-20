# Project background

Metrologist is an Android app that measures a flat surface from one camera photo
and a reference stick of known dimensions. The user captures a photo, marks the
object and stick corners, chooses wall or floor/table, and receives dimensions
and an exportable result. Camera intrinsics, image coordinates and exposure-time
gravity must agree. Pure Kotlin geometry and presentation code sit underneath
CameraX capture and Jetpack Compose UI.

We are repairing nine reviewed logic defects, not redesigning the product:
rectangle vanishing points, capture coordinate alignment, solver eligibility and
confidence, surface selection, recoverable measurement failures, marker geometry,
gravity direction/level display, capture retry, and finite reference dimensions.

The repair graph's edges are contracts. Its execution plan starts with a shared
success/failure schema and revision-bound result invalidation, opens independent
branches, and joins them for solver and UI integration. Workers share a checkout
but receive disjoint write reservations. The supervisor owns scheduling and runs
verification only when the checkout is quiet. An individual milestone passing
does not mean all neighbouring contracts or physical-device behavior are proven.

Shared conventions: physical gravity points down; camera axes are x-right,
y-down, z-forward in the declared image frame; lengths are metres internally;
object corners are TL, TR, BR, BL. Stick boxes may have either cyclic winding.
Failures are explicit outcomes, never fabricated zero-valued measurements.
Results belong to an image/input revision and become unusable after input changes.
Calibration quality and missing/stale gravity must remain visible in diagnostics.

Geometry, core and stick code must remain free of Android imports. Keep each
code/test/configuration file within 200 lines, extracting helpers inside your
reserved scope. Existing source and tests may encode old assumptions: change
them only when the assigned contract establishes the corrected behavior.
